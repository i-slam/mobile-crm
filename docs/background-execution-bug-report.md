# Bug report: backgrounded app receives no IPC of any kind on Tecno/HiOS device (Android 13)

## Summary

On a specific physical device (Tecno Spark 20C, HiOS/Transsion, Android 13), an app process that is alive, healthy, and has an active foreground service running receives **zero inter-process communication of any kind** once it is no longer the foreground/recently-used app — not a `ContentObserver` callback, not an explicit `BroadcastReceiver` broadcast, not (apparently) `TelephonyCallback` delivery. This happens despite every standard Android background-execution exemption (Doze allowlist, "Autostart," "Unrestricted" battery usage) already being granted, and despite using an **explicit**, permission-less broadcast that per Android's own documentation should not be subject to the implicit-broadcast background-execution limits at all.

We need help identifying whether this is: (a) a known, documented Transsion/HiOS behavior with a known workaround, (b) a real Android platform bug specific to this build, or (c) something about our test methodology we haven't considered. We are not looking for "check your battery optimization settings" — we have already verified and ruled that out with device-level evidence below.

## Environment

```
Manufacturer:     TECNO (Transsion Holdings)
Model:            TECNO BG7 (marketed as "Tecno Spark 20C")
Android version:  13 (API level 33)
Security patch:   2026-01-01
Build fingerprint: TECNO/BG7-OP/TECNO-BG7:13/TP1A.220624.014/260121V2708:user/release-keys
Build display ID: BG7-XE674SABCDEFLMNQR-T-OP-260121V2708
OS skin:          HiOS (Transsion's Android skin)
```

Test/debug environment: Windows PC with Android SDK platform-tools connected via USB (`adb`), USB debugging enabled, developer options enabled. All evidence below was captured directly from this device via `adb shell` commands run against the connected device (single device, verified via `adb devices` throughout).

## App context (only what's relevant to this bug)

The app in question (package `com.aistudio.callpopup.crm`) is a small Android/Kotlin/Jetpack Compose CRM companion: it watches for phone calls ending, then shows a small floating overlay window (a note-taking popup) so a salesperson can log what was discussed. It is not a dialer replacement and does not use `InCallService`/`ROLE_DIALER`.

Relevant components:

- **`CallMonitorService`** — an Android foreground service (`startForegroundService()`, persistent low-importance notification, `foregroundServiceType="specialUse"`). Runs continuously. Registers:
  - `TelephonyManager.registerTelephonyCallback()` (API 31+) to observe `RINGING`/`OFFHOOK`/`IDLE` call state transitions.
  - A `ContentObserver` on `CallLog.Calls.CONTENT_URI` (registered via `contentResolver.registerContentObserver(uri, true, observer)`) as a second, independent path to catch calls that never surface a `RINGING` state (carrier-rejected, Do Not Disturb, OEM call-screening) but still produce a `MISSED` call-log row.
  - Both dispatch onto a dedicated `HandlerThread` (not main), via a custom `Executor` for the `TelephonyCallback` and by passing that thread's `Handler` to the `ContentObserver` constructor.
- On call end, it shows an overlay via `WindowManager.addView()` of a `ComposeView`, from a **second Service running in its own process** (`android:process=":overlay"` in the manifest) — isolated into its own process specifically because `addView()` was found to occasionally hang the main thread indefinitely on this exact device (separate finding, already fixed by the process split — not the subject of this report).

The bug in this report is specifically: **none of the above (TelephonyCallback, ContentObserver) appears to fire at all while the app is backgrounded**, even though the foreground service and all its threads remain alive and idle (confirmed via `top -H`, not hung).

## Minimal reproduction

Because the app above has enough moving parts (Room, WorkManager, OkHttp/WebSocket) to raise "maybe it's one of those" doubts, we built a **separate, minimal standalone app** with no dependencies beyond Jetpack Compose, specifically to isolate this one behavior. Full source is available on request; the relevant pieces:

**`AndroidManifest.xml`** (trimmed):

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<receiver
    android:name=".TriggerReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.aistudio.popuptest.ACTION_SHOW_POPUP" />
    </intent-filter>
</receiver>

<service android:name=".KeepAliveForegroundService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

**`TriggerReceiver.kt`** (complete):

```kotlin
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_POPUP) return
        Log.i("TriggerReceiver", "onReceive: variant=${intent.getStringExtra(EXTRA_VARIANT)}")
        // ... starts an overlay Service ...
    }
}
```

No `android:permission` on the receiver. No manifest `<intent-filter>` restrictions beyond the one custom action. This is about as simple as a `BroadcastReceiver` can be.

### Repro steps

1. Install the app, grant `SYSTEM_ALERT_WINDOW` ("Display over other apps").
2. Start `KeepAliveForegroundService` (confirmed via `dumpsys activity services` to have `isForeground=true`).
3. Press **Home** (do not swipe the app away from Recents — the process and its foreground service stay alive; confirmed via `ps -A` and `dumpsys activity services` throughout).
4. From the host PC, send a fully **explicit** broadcast naming the exact component:
   ```
   adb shell am broadcast -n com.aistudio.popuptest/.TriggerReceiver \
       -a com.aistudio.popuptest.ACTION_SHOW_POPUP --es variant same
   ```
5. `am broadcast` reports `Broadcast completed: result=0` (system accepted it).
6. **`TriggerReceiver.onReceive()`'s `Log.i` line never appears in logcat.** No exception, no ANR, no crash — the process is simply never invoked.

We also tested an **implicit** broadcast (no `-n`/`-p`) first, which is expected to be blocked by Android 8+'s implicit-broadcast background-execution limits and was — the system logged exactly that:

```
BroadcastQueue: Background execution not allowed: receiving Intent { act=com.aistudio.popuptest.ACTION_SHOW_POPUP ... } to com.aistudio.popuptest/.TriggerReceiver
```

That log line **does not appear** for the explicit-component-targeted broadcast in step 4 — the system doesn't report *refusing* delivery, it just... doesn't deliver it. No log anywhere (checked with `adb logcat -d` unfiltered, not just `--pid`-filtered) suggests the system even attempted to wake the process for this broadcast.

## What we've already ruled out, with evidence

| Candidate cause | Evidence it's not this |
|---|---|
| **App not exempt from Doze** | `adb shell dumpsys deviceidle whitelist \| grep <pkg>` → `user,<pkg>,<uid>` (present, user-granted) |
| **Device currently in Doze** | `adb shell dumpsys deviceidle \| grep mState` → `mState=ACTIVE mLightState=ACTIVE` (not idling at all) |
| **OEM "Autostart" disabled** | Verified via the OEM's own Phone Master app (`com.transsion.phonemaster`, deep link `phonemaster://com.transsion.phonemaster/AutoStart`, activity `com.cyin.himgr.autostart.AutoStartActivity`) — screenshot confirms toggle already "Allowed" for this app |
| **OEM "Battery Usage" restricted** | Standard Settings → App info → Battery screen confirms "Unrestricted" already selected (not "Optimized" or "Restricted") |
| **Implicit-broadcast background limits (Android 8+)** | Tested with a fully explicit `-n <pkg>/<component>` target, which per Android documentation should bypass this limit entirely. Still no delivery. |
| **No active foreground service (cached-app freezer)** | Tested with `KeepAliveForegroundService` confirmed running (`isForeground=true` in `dumpsys`) at the moment of the broadcast. No difference. |
| **Process actually hung/frozen** | `top -H -p <pid>` shows all threads in state `S` (interruptible sleep, i.e. genuinely idle) at 0% CPU, not `D` (uninterruptible) — this is not a deadlock, the process is simply never woken |
| **Wrong user profile** | Confirmed via `dumpsys package <pkg> \| grep "User "` that the app is `installed=true` for the same user (`User 0`, the active "Owner" profile, confirmed via `am get-current-user`) that all `adb shell` commands operate against |

We separately confirmed the app's own detection/popup code is correct: with the app in the foreground (or very recently used), the full pipeline — `ContentObserver` fires → number/duration resolved from `CallLog` → overlay `Service` starts in its own process → `WindowManager.addView()` succeeds → popup visibly renders on screen — works end to end, repeatedly, on this exact device. The failure is specifically and only "app has been backgrounded for more than a few seconds → no IPC reaches it at all."

## What we haven't tried / don't have access to

- A full `adb bugreport` (haven't pulled/analyzed one for cgroup-freezer or `ActivityManager`/`OomAdjuster` state at the moment of a failed delivery attempt).
- `adb shell dumpsys activity processes` at the *exact* moment of a failed broadcast, to see what `oom_adj`/process state the target is in when the system supposedly tries to deliver to it.
- Root access (device is not rooted) — can't inspect `/sys/fs/cgroup/.../cgroup.freeze` or equivalent directly.
- Any Transsion/HiOS developer documentation — we could not find official developer-facing docs for HiOS's background-process policy; the community resource `dontkillmyapp.com/tecno` covers Autostart/battery but not this deeper behavior.
- Testing on a second Transsion/HiOS device to see if this is universal to the OEM or specific to this exact build/firmware (`260121V2708`).

## What we're asking for

1. Is this a **known, named Transsion/HiOS behavior** (e.g., a stricter cgroup-freezer policy than stock AOSP, applied even to apps with an active foreground service)? If so, is there a documented app-side workaround or exemption request beyond what we've already tried?
2. Is there a **specific `adb`/`dumpsys` command** that would show *why* the broadcast dispatch silently no-ops for this process — i.e., where in the pipeline (system_server scheduling vs. OEM policy layer vs. kernel freezer) the drop actually happens?
3. Is a **full `adb bugreport`** likely to contain the missing piece of evidence, and if so what section should we grep for?
4. Are there other Android developers who have solved *this specific* problem (not the general "my app gets killed" complaint, but specifically "explicit broadcast + confirmed-alive foreground service + confirmed-not-Doze still gets zero IPC") on Transsion devices, and what did they do?
5. Barring an app-level fix: is a full `InCallService`/default-dialer-role architecture (which we've separately confirmed has fundamentally different, more privileged binding semantics driven directly by the Telecom framework) actually known to be exempt from this same restriction on Transsion devices, or is that assumption itself untested?
