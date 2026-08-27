# Call Notes & Quick Share — Technical Specification

**Package:** `com.aistudio.callpopup.crm` · **Platform:** Android (Kotlin, Jetpack Compose) · **Min SDK:** 24 · **Target SDK:** 36

## 1. Product Overview

Call Notes & Quick Share is a post-call productivity tool for showroom / sales staff. The moment a phone call ends, the app pops up a note card over whatever the rep is doing next, so they can capture what was discussed while it's fresh — without opening a separate CRM app or waiting until they're back at a desk.

**Target user:** a salesperson or reception staff at a single physical location (a "showroom") who takes a high volume of short calls and needs to (a) leave a quick note per call, (b) tag the call for later filtering, and (c) immediately follow up with the caller over WhatsApp/SMS using a pre-written message (location, catalog link, appointment booking) — all in under 15 seconds, without leaving the popup.

Call metadata and notes are also queued and pushed to a configurable WebSocket backend, so a separate CRM/dashboard system can ingest them in near-real-time.

## 2. Feature List

### Call detection & popup
- Detects call state transitions (ringing → answered/missed → ended) via `TelephonyCallback` (API 31+) / `PhoneStateListener` (fallback) running inside a foreground service, cross-referenced with `CallLog` for a phone number when the OS doesn't supply one directly.
- Shows a popup immediately after the call ends: as a system overlay window (`TYPE_APPLICATION_OVERLAY`, works from the background) when overlay permission is granted, or as a translucent Activity as a fallback.
- Resolves the caller's saved contact name via `ContactsContract.PhoneLookup`.
- The detected phone number is editable in the popup (tap the number) in case detection or contact lookup got it wrong.

### Note-taking & tagging
- Free-text notes field with one-tap quick snippets ("Sent Location", "Budget Discussed", etc.).
- Configurable tag chips (emoji + label), multi-select per call, with an inline "add custom tag" flow.

### WhatsApp / SMS quick-share
- One-tap "WhatsApp Location" and "SMS" buttons send the showroom's Google Maps link + address using a template.
- A small template library (Location, Catalog, Appointment, Thank-you, plus user-defined) with token substitution: `{number}`, `{showroom_name}`, `{address}`, `{maps_url}`.
- Falls back to the system share sheet / clipboard copy if WhatsApp isn't installed.

### History
- Local call history list (search by number/name/notes, filter by tag or pending-sync status).
- Per-record detail view showing the exact JSON that was/will be sent over WebSocket.
- CSV export of the full call history, shared via the system share sheet.
- "Clear all history" (Settings → Danger Zone), with a confirmation prompt.

### Vehicle inventory
- A local vehicle inventory (make, model, year, price, fuel/transmission/mileage/color specs, showroom location, registration notes), seeded on first launch from `app/src/main/assets/seed_inventory.json`.
- A dedicated **Inventory** tab (bottom nav) to browse/search/filter by status, add/edit vehicles, and toggle AVAILABLE/SOLD.
- The post-call popup has a collapsible **"Vehicles Discussed"** checklist of AVAILABLE vehicles; selections are saved on the call record (`selectedVehicleIds`) and sent as `vehicle_ids` in the WebSocket payload (§5). A "Send Selected Vehicles via WhatsApp" button formats the selection into a message the same way the location/catalog quick-share buttons do.

### WebSocket sync
- Configurable server URL + optional bearer token (Settings/WebSocket screen).
- Every saved call record is sent as a structured JSON packet (§5) immediately; on failure it's kept `PENDING` in the local Room database and retried automatically when connectivity returns or the user taps "Flush Queue".
- A live packet/frame inspector shows outbound/inbound/system events for debugging against a real backend.

### Settings
- Showroom name, address, and Google Maps link (used to fill message templates).
- WhatsApp template manager (add/edit/delete/reset to defaults).
- Custom tag manager.
- Danger Zone: clear all local call history.

### Permissions & reliability
- First-run onboarding screen explains why phone/contacts/overlay access is requested before the system dialogs fire.
- Runtime permissions: `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`, `POST_NOTIFICATIONS` (API 33+).
- Overlay permission (`SYSTEM_ALERT_WINDOW`) for reliable background popups.
- "Ignore Battery Optimizations" prompt to reduce the chance of OEM battery managers killing the background monitor.
- A foreground service (with a persistent low-priority notification) keeps call detection and the WebSocket connection alive; it restarts automatically on device boot if it was previously enabled.

## 3. Architecture

```
com.example/
├── data/
│   ├── model/        CallRecord, AppSettings, WhatsAppTemplate, WebSocket packet DTOs
│   ├── db/            Room database (AppDatabase)
│   ├── dao/            CallRecordDao
│   └── repository/    CallRepository — single source of truth (DB + SharedPreferences + WS client)
├── service/
│   ├── CallMonitorService          Foreground service: call-state detection, network monitoring, WS lifecycle
│   └── FloatingWindowOverlayService  Adds/removes the system-overlay popup window
├── overlay/
│   ├── CallOverlayActivity         Translucent-Activity fallback popup (used only without overlay permission)
│   └── CallOverlayDialogContent    The shared Compose UI rendered by both popup paths
├── receiver/
│   └── BootReceiver                Restarts CallMonitorService after device boot / app update
├── websocket/
│   └── CallWebSocketClient          OkHttp WebSocket wrapper: connect/reconnect, send, live log stream
├── util/
│   ├── CallLogHelper, PermissionUtils, ShareHelper, CsvExportHelper
└── ui/
    ├── screens/        Dashboard (Home), Call Logs (History), WebSocket, Showroom (Settings), Onboarding
    ├── navigation/      Screen (bottom-nav routes)
    └── theme/           Material 3 theme
```

**Single call-detection path.** Call state is tracked exclusively inside `CallMonitorService` via `TelephonyCallback`/`PhoneStateListener`. There is intentionally no separate manifest-registered `BroadcastReceiver` for `PHONE_STATE` — an earlier revision had both, which caused the popup to fire more than once per call from duplicate, racing listeners. `BootReceiver` only restarts the service; it does not itself detect calls.

**Popup delivery.** `CallMonitorService` decides how to present the popup at call-end:
- **Overlay permission granted** → `FloatingWindowOverlayService.show(...)` adds a `TYPE_APPLICATION_OVERLAY` window directly from the service. This is the reliable path: it is not subject to Android 10+'s restriction on starting Activities from the background.
- **Overlay permission missing** → `CallOverlayActivity.launch(...)` is attempted as a best-effort fallback; on Android 10+ this may be silently blocked by the OS while the app is backgrounded (see §7).

## 4. Data Model

**`CallRecord`** (Room entity, table `call_records`)

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Autogenerated PK |
| `phoneNumber` | `String` | Editable by the rep before saving |
| `callerName` | `String` | From Contacts lookup, or a placeholder |
| `callType` | `String` | `INCOMING` / `OUTGOING` / `MISSED` / `REJECTED` |
| `startTimeMillis`, `endTimeMillis` | `Long` | Epoch millis |
| `durationSeconds` | `Int` | |
| `notes` | `String` | Free text |
| `tags` | `List<String>` | Stored as JSON via a Room `TypeConverter` |
| `actionsTaken` | `List<String>` | e.g. `SENT_SHOWROOM_LOCATION_WHATSAPP` |
| `syncStatus` | `String` | `PENDING` / `SYNCED` / `FAILED` |
| `syncedAtMillis` | `Long?` | |

**`AppSettings`** (persisted in `SharedPreferences`, not Room) — WebSocket URL/token/auto-reconnect, showroom name/address/maps URL, overlay toggle, custom tag list.

**`WhatsAppTemplate`** — `id`, `title`, `description`, `iconType`, `templateText` (with `{number}` / `{showroom_name}` / `{address}` / `{maps_url}` placeholders).

**`Vehicle`** (Room entity, table `vehicles`) — `id` (PK, e.g. `"VEH-008"`), `status` (`AVAILABLE`/`SOLD`), `make`, `model`, `year`, and embedded `price` (`amount`, `currency`), `location` (`type`, `showroomName`, `city`), `specifications` (`fuelType`, `transmission`, `fiscalPowerCV`, `mileageKm`, `color`), `registration` (`plate`, `notes`). The same class is used both as the Room entity and as the shape Moshi parses `seed_inventory.json` into — field names match the JSON keys exactly.

## 5. WebSocket Contract

The client is the source of truth for this contract today — no reference backend ships with this repo. A backend implementation should accept a JSON text frame per call save, shaped as:

```json
{
  "event": "call_note_captured",
  "message_id": "1f2e3d4c-...-uuid",
  "timestamp": 1735000000000,
  "timestamp_iso": "2026-08-25T14:03:20Z",
  "record_id": 42,
  "call_metadata": {
    "phone_number": "+1 (555) 382-9912",
    "caller_name": "Sarah Jenkins",
    "call_type": "INCOMING",
    "duration_seconds": 115,
    "start_time_iso": "2026-08-25T14:01:25Z",
    "end_time_iso": "2026-08-25T14:03:20Z"
  },
  "user_input": {
    "notes": "Interested in the Italian marble collection...",
    "tags": ["🔥 Hot Lead", "📍 Showroom Visit"],
    "actions_taken": ["SENT_SHOWROOM_LOCATION_WHATSAPP"],
    "vehicle_ids": ["VEH-008", "VEH-014"]
  },
  "showroom_info": {
    "showroom_name": "Downtown Luxury Showroom",
    "showroom_address": "100 Grand Avenue, Financial District, New York, NY 10001",
    "maps_url": "https://maps.google.com/?q=..."
  },
  "device_info": {
    "app_name": "Call Notes & Quick Share",
    "app_version": "1.0",
    "platform": "Android"
  }
}
```

Connection: standard `ws://`/`wss://`, optional `Authorization: Bearer <token>` header, `X-Client-App: CallNotesQuickShare-Android` header. The client sends a ping frame `{"type":"ping","timestamp":...}` on demand (WebSocket screen "Send Ping" action) and measures round-trip latency from any inbound frame that follows. Any server response is only logged (for the in-app frame inspector); the client does not currently parse or act on inbound messages. If a save can't be delivered immediately (offline/socket error), the record stays `PENDING` locally and is retried on reconnect or manual "Flush Queue".

There is no authentication/authorization model beyond the optional static bearer token — this is appropriate for a single-showroom internal tool, not for a multi-tenant deployment.

## 6. Permissions

| Permission | Why |
|---|---|
| `READ_PHONE_STATE` | Detect call state transitions |
| `READ_CALL_LOG` | Resolve number/duration when not present in the state broadcast |
| `READ_CONTACTS` | Show the caller's saved name |
| `SYSTEM_ALERT_WINDOW` | Show the popup as a system overlay window (reliable background delivery) |
| `POST_NOTIFICATIONS` (API 33+) | Required to show the foreground-service notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep call detection + WebSocket alive while backgrounded |
| `RECEIVE_BOOT_COMPLETED` | Restart the monitor after device reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reduce the chance of OEM battery managers killing the service |
| `INTERNET`, `ACCESS_NETWORK_STATE` | WebSocket connectivity |

## 7. Call-detection reliability

Two mechanisms specifically target reliability issues found in real-device testing (calls, especially missed ones, silently not producing a popup):

- **Critical vs. full permission sets.** `PermissionUtils.getCriticalCallPermissions()` (`READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`) is deliberately separate from the full requested set, which also includes `POST_NOTIFICATIONS` on API 33+. `CallMonitorService.start()` is gated on the critical set only. Earlier this was one combined set, which meant a user who denied just the notification prompt (very common, and not actually required for call detection to function) silently disabled the whole feature with no error shown anywhere.
- **Call-log query retry.** The OS can write a call-log entry (missed calls especially) a beat after the telephony state already reports IDLE. `CallLogHelper.getMostRecentCallWithRetry()` retries the query a few times over ~2 seconds, accepting the first result timestamped at or after the call actually started, instead of a single immediate query that can race the write and return nothing or a stale earlier call.
- **Watchdog.** `CallMonitorWatchdogWorker` (WorkManager, every 15 minutes — WorkManager's floor for periodic work) restarts `CallMonitorService` if it should be running (critical permissions granted, overlay enabled in Settings) but isn't. This is a backstop, not the primary mechanism — the foreground service itself, `START_STICKY`, and `BootReceiver` (restart on device boot) handle the common cases; the watchdog catches everything else (OOM kills, some OEM battery managers) within 15 minutes instead of leaving the app dead until next manually opened.
- **Telephony listener registration retry.** If registering the `TelephonyCallback`/`PhoneStateListener` throws on `onCreate()`, it retries twice with backoff instead of silently running with no call detection for the service's entire lifetime.
- **CallLog observer as a second missed-call detection path.** A call rejected/screened below the telephony API (carrier-side rejection, Do Not Disturb, some OEM call-screening) never surfaces a `RINGING` state to the app at all — the telephony state machine never sees it, only a `MISSED` row appearing later in the call log. `CallMonitorService` also registers a `ContentObserver` on `CallLog.Calls.CONTENT_URI` and triggers the popup directly off a new `MISSED` entry when that happens. Both paths funnel through the same `showPopupForCall()`, which dedupes by phone number within an 8-second window so a call either path catches doesn't show twice.
- **Overlay window runs in its own process.** Real-device testing (Tecno Spark 20C / HiOS) confirmed `WindowManager.addView()` in `FloatingWindowOverlayService` reliably hangs indefinitely on this device — a Binder call to the OEM's WindowManagerService that never returns (verified via `adb shell top`: the process sits in kernel state `D`, uninterruptible sleep, at 0% CPU, not merely slow). Because that Service originally shared a process — and therefore a main thread — with `CallMonitorService`, this hang froze the *entire app*, including call detection, after the first overlay attempt: exactly matching an earlier report of "the popup only shows right after opening the app" (each reopen bought one working attempt before the process wedged again).
  - First attempt: move `WindowManager`/`ComposeView` work in `FloatingWindowOverlayService` onto a dedicated `HandlerThread`. This *broke the overlay outright* rather than avoiding the hang — Compose's `ComposeView` calls `LifecycleRegistry.addObserver()` internally (via `WindowRecomposer`, triggered from `onAttachedToWindow()`), and AndroidX Lifecycle unconditionally throws `IllegalStateException` if that happens off the main thread. `addView()` for a `ComposeView` genuinely cannot be moved off main.
  - Actual fix: `FloatingWindowOverlayService` runs in its own process (`android:process=":overlay"` in `AndroidManifest.xml`). A hang in that process's main thread can no longer affect `CallMonitorService`'s process. `CallMonitorService`'s `TelephonyCallback` registration and `CallLog` `ContentObserver` dispatch were also moved onto their own `HandlerThread` (independent improvement, not dependent on the process split) so call detection is decoupled from a hang anywhere else in the main process too.
  - Trade-off: `CallOverlayDialogContent` reads `CallRepository`/`VehicleRepository`/`CallWebSocketClient` singletons, which now get separate instances in the `:overlay` process. `AppDatabase` has `enableMultiInstanceInvalidation()` on so Room's Flow-based queries stay in sync across the process boundary; the WebSocket connection and DataStore-backed settings do not have equivalent multi-process synchronization, so the overlay process may briefly use a stale settings snapshot or open a redundant WebSocket connection when dispatching a call record. Acceptable given the alternative was the entire app freezing.

## 8. Known Limitations

- **Background popup without overlay permission.** If the user declines "Display over other apps," the fallback (`CallOverlayActivity`) can be silently blocked by Android 10+'s background-activity-start restriction. The overlay permission is effectively required for reliable operation, not optional.
- **OEM battery managers.** Even with the "ignore battery optimizations" exemption granted and the watchdog described above, some manufacturers (notably MIUI/Xiaomi, some Huawei/Honor, and older Samsung builds) apply additional, non-standard background-kill policies beyond what Android's own API exposes and can prevent WorkManager itself from running promptly. There is no fully reliable way to prevent this from app code alone; users on aggressive OEM skins may need to manually allow "autostart" or disable manufacturer-specific battery restrictions.
- **No backend included.** The WebSocket target defaults to a public echo-test server (`wss://echo.websocket.events`), which bounces the payload back but does not persist anything. A real deployment needs a backend implementing §5's contract.
- **Single static bearer token**, no per-user auth — fine for one showroom, not for multi-tenant use.
- **No automated instrumentation tests** beyond the AI-Studio-generated boilerplate (`ExampleUnitTest`, `ExampleRobolectricTest`, a screenshot test). None of the flows described above have test coverage yet.

## 9. Build & Run

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK (needs a keystore — see signingConfigs in app/build.gradle.kts)
./gradlew test              # unit tests (Robolectric + Roborazzi)
./gradlew connectedAndroidTest  # instrumented tests (needs a device/emulator)
```

Optional: create `.env` (see `.env.example`) with `GEMINI_API_KEY` if you want to build on the Firebase AI/Gemini dependency already declared in `app/build.gradle.kts`; it is not currently wired into any feature.

Needs a JDK 17+ and an Android SDK (`platforms;android-36`, `platforms;android-36.1`, `build-tools;36.0.0`) with `local.properties` pointing `sdk.dir` at it, or open the project in a recent Android Studio, which provisions all of that automatically.

### Windows setup + USB install script

[scripts/setup_and_install.ps1](scripts/setup_and_install.ps1) automates the whole path on a bare Windows machine: installs a JDK 21 (via winget) and the Android SDK command-line tools if missing, generates `local.properties`, builds the debug APK, and `adb install`s it on a USB-connected device with USB debugging enabled. Re-running it is safe — every step is skipped if its output already exists.

```powershell
.\scripts\setup_and_install.ps1              # full setup + build + USB install
.\scripts\setup_and_install.ps1 -SkipBuild   # only install tooling, build/install later
```

It checks free disk space up front (`-MinFreeGb`, default 6) and refuses to start if there isn't enough — a full JDK + Gradle + Android SDK + dependency-cache footprint is multiple GB, and a build that runs out of disk space mid-way tends to leave partially-written caches behind rather than failing cleanly.
