<#
.SYNOPSIS
    One-shot Android build environment setup + debug build + USB install for this repo.

.DESCRIPTION
    This machine ships with no JDK, no Android SDK, and no committed Gradle wrapper binary.
    This script installs everything needed (JDK 21, Android SDK command-line tools +
    platform-tools + platforms;android-36/36.1 + build-tools;36.0.0, and a Gradle wrapper
    for this repo), then builds app-debug.apk and installs it on a USB-connected device
    with USB debugging enabled.

    Safe to re-run: every step is skipped if its output already exists, so if a previous
    run died partway (e.g. ran out of disk space), just fix the underlying problem and
    run the script again.

    By default, install uninstalls any existing copy of the app first (see
    -KeepExistingInstall to skip this) - a debug build re-signed with a locally generated
    debug.keystore won't match a build installed via Android Studio or an earlier key, so a
    plain `adb install -r` fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE in that case. The
    tradeoff is that the app's local data/settings on the device get wiped every run.

.PARAMETER ToolsDir
    Where the JDK-independent tooling (Gradle, Android SDK) AND the Gradle dependency
    cache (GRADLE_USER_HOME - normally ~500MB-2GB+ on a cold build) are installed.
    Defaults to D:\android-tools specifically because C: on this machine runs close to
    full; only the JDK itself (~400MB, installed via winget) has to live on C:.
    GRADLE_USER_HOME is persisted as a user environment variable so it stays redirected
    even outside this script (Android Studio, a bare `gradlew` call, etc).

.PARAMETER SkipBuild
    Only install tooling + generate the wrapper; don't build or install the APK.

.EXAMPLE
    .\scripts\setup_and_install.ps1
    Full run: install tooling if missing, build the debug APK, install it over USB.

.EXAMPLE
    .\scripts\setup_and_install.ps1 -SkipBuild
    Only get the tooling in place; build/install later by re-running without -SkipBuild.
#>

param(
    [string]$ToolsDir = "D:\android-tools",
    [string]$JdkWingetId = "EclipseAdoptium.Temurin.21.JDK",
    [string]$GradleVersion = "9.3.1",
    [string]$MinFreeGbTools = 6,
    [string]$MinFreeGbSystem = 1,
    [string]$ApplicationId = "com.aistudio.callpopup.crm",
    [switch]$SkipBuild,
    [switch]$KeepExistingInstall
)

$ErrorActionPreference = "Continue"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ToolsDrive = (Split-Path -Qualifier $ToolsDir) -replace ':',''

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

function Assert-FreeSpace([string]$driveLetter, [double]$minGb, [string]$label) {
    $freeGb = (Get-PSDrive $driveLetter).Free / 1GB
    if ($freeGb -lt $minGb) {
        Write-Error ("Only {0:N2} GB free on {1}:. Need at least {2} GB free on {1}: for {3}. Free up space and re-run." -f $freeGb, $driveLetter, $minGb, $label)
        exit 1
    }
    Write-Host ("Free space on {0}:  {1:N2} GB (OK)" -f $driveLetter, $freeGb)
}

# ---------------------------------------------------------------------------
# 0. Disk space guard - this exact script ran C: out of space mid-build once;
#    fail fast and clearly instead of dying halfway through a multi-GB setup.
#    The heavy stuff (Gradle, Android SDK, dependency cache) is checked against
#    $ToolsDir's drive; C: only needs headroom for the JDK + normal build churn.
# ---------------------------------------------------------------------------
Write-Step "Checking free disk space"
Assert-FreeSpace $ToolsDrive $MinFreeGbTools "Gradle/Android SDK/dependency cache"
if ($ToolsDrive -ne "C") {
    Assert-FreeSpace "C" $MinFreeGbSystem "the JDK install and normal build churn"
}

# ---------------------------------------------------------------------------
# Redirect GRADLE_USER_HOME (dependency cache, wrapper distributions, daemon
# logs) onto $ToolsDir's drive - this is what actually filled C: last time,
# not the JDK/SDK themselves. Persisted at User scope so it sticks outside
# this script too.
# ---------------------------------------------------------------------------
$gradleUserHome = Join-Path $ToolsDir ".gradle-home"
New-Item -ItemType Directory -Force -Path $gradleUserHome | Out-Null
$env:GRADLE_USER_HOME = $gradleUserHome
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", $gradleUserHome, "User")
Write-Host "GRADLE_USER_HOME = $gradleUserHome (persisted)"

# ---------------------------------------------------------------------------
# 1. JDK 21
# ---------------------------------------------------------------------------
Write-Step "Locating / installing JDK 21"

function Find-WorkingJdk {
    $searchRoots = @("C:\Program Files\Eclipse Adoptium", (Join-Path $ToolsDir "Eclipse Adoptium"))
    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem $root -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($candidate -and (Test-Path (Join-Path $candidate.FullName "bin\java.exe"))) {
            return $candidate.FullName
        }
    }
    return $null
}

# Validate bin\java.exe actually exists, not just the directory - a JDK
# uninstall/rollback can leave a directory tree behind with core files gone.
$jdkHome = Find-WorkingJdk
if (-not $jdkHome) {
    Write-Host "No working JDK 21 found - installing via winget..."
    winget install --id $JdkWingetId --silent --accept-package-agreements --accept-source-agreements
    $jdkHome = Find-WorkingJdk
    if (-not $jdkHome) { Write-Error "JDK install did not produce a working java.exe under 'C:\Program Files\Eclipse Adoptium' or '$ToolsDir\Eclipse Adoptium'."; exit 1 }
}
$env:JAVA_HOME = $jdkHome
$env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
Write-Host "JAVA_HOME = $env:JAVA_HOME"

# ---------------------------------------------------------------------------
# 2. Local Gradle install (only used once, to generate this repo's wrapper)
# ---------------------------------------------------------------------------
Write-Step "Locating / installing local Gradle $GradleVersion (used only to generate the wrapper)"
New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
$gradleDir = Join-Path $ToolsDir "gradle-$GradleVersion"
$gradleBat = Join-Path $gradleDir "bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
    $gradleZip = Join-Path $ToolsDir "gradle-$GradleVersion-bin.zip"
    Write-Host "Downloading Gradle $GradleVersion..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $gradleZip
    Expand-Archive -Path $gradleZip -DestinationPath $ToolsDir -Force
    Remove-Item $gradleZip -Force
}
Write-Host "Gradle at $gradleBat"

# ---------------------------------------------------------------------------
# 3. Android SDK command-line tools + packages
# ---------------------------------------------------------------------------
Write-Step "Locating / installing Android SDK"
$sdkRoot = Join-Path $ToolsDir "Sdk"
$sdkManager = Join-Path $sdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"

if (-not (Test-Path $sdkManager)) {
    Write-Host "Resolving current Android cmdline-tools download URL..."
    $page = Invoke-WebRequest -Uri "https://developer.android.com/studio" -UseBasicParsing
    $match = [regex]::Match($page.Content, 'https://dl\.google\.com/android/repository/commandlinetools-win-[0-9]+_latest\.zip')
    if (-not $match.Success) { Write-Error "Could not find the Windows cmdline-tools download URL on the Android developer page."; exit 1 }
    $ctUrl = $match.Value
    $ctZip = Join-Path $ToolsDir "commandlinetools-win.zip"
    Write-Host "Downloading $ctUrl ..."
    Invoke-WebRequest -Uri $ctUrl -OutFile $ctZip

    $tmp = Join-Path $ToolsDir "sdk_tmp"
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $ctZip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot "cmdline-tools") | Out-Null
    Move-Item (Join-Path $tmp "cmdline-tools") (Join-Path $sdkRoot "cmdline-tools\latest") -Force
    Remove-Item $tmp -Recurse -Force
    Remove-Item $ctZip -Force
}

if (-not (Test-Path $adb) -or -not (Test-Path (Join-Path $sdkRoot "platforms\android-36.1"))) {
    Write-Host "Accepting Android SDK licenses..."
    $yesFile = Join-Path $ToolsDir "yes.txt"
    (1..60 | ForEach-Object { "y" }) -join "`n" | Set-Content $yesFile -NoNewline
    Add-Content $yesFile "`n"
    cmd /c "set JAVA_HOME=$($env:JAVA_HOME)&& `"$sdkManager`" --licenses < `"$yesFile`"" | Out-Null
    Remove-Item $yesFile -Force -ErrorAction SilentlyContinue

    Write-Host "Installing platform-tools, platforms;android-36, platforms;android-36.1, build-tools;36.0.0..."
    cmd /c "set JAVA_HOME=$($env:JAVA_HOME)&& `"$sdkManager`" platform-tools `"platforms;android-36`" `"platforms;android-36.1`" `"build-tools;36.0.0`""
}
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkRoot, "User")
$env:ANDROID_HOME = $sdkRoot
Write-Host "Android SDK at $sdkRoot (ANDROID_HOME persisted)"

# ---------------------------------------------------------------------------
# 4. Gradle wrapper + local.properties for this repo
# ---------------------------------------------------------------------------
Write-Step "Generating Gradle wrapper for the repo (if missing)"
Set-Location $RepoRoot
if (-not (Test-Path "$RepoRoot\gradlew.bat")) {
    & $gradleBat wrapper --gradle-version $GradleVersion --distribution-type bin
}

$localProps = Join-Path $RepoRoot "local.properties"
"sdk.dir=$($sdkRoot -replace '\\','\\\\')" | Set-Content $localProps
Write-Host "Wrote $localProps"

if ($SkipBuild) {
    Write-Host "`n-SkipBuild set: tooling is ready. Run this script again without -SkipBuild to build + install." -ForegroundColor Yellow
    exit 0
}

# ---------------------------------------------------------------------------
# 5. Build the debug APK
# ---------------------------------------------------------------------------
Write-Step "Building debug APK (this downloads all AGP/Kotlin/Compose/Room/Firebase dependencies on first run - can take several minutes)"
Assert-FreeSpace $ToolsDrive $MinFreeGbTools "the dependency cache download"
& "$RepoRoot\gradlew.bat" assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed - see output above."; exit 1 }

$apk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { Write-Error "Build reported success but $apk was not found."; exit 1 }
Write-Host "Built: $apk"

# ---------------------------------------------------------------------------
# 6. Install over USB
# ---------------------------------------------------------------------------
Write-Step "Installing on USB-connected device"
Write-Host "Make sure: Developer Options > USB debugging is ON, the phone is plugged in, and you've tapped 'Allow' on the RSA fingerprint prompt on the phone screen."
& $adb start-server
& $adb devices
if (-not $KeepExistingInstall) {
    # Debug builds get re-signed with a locally generated debug.keystore, which won't match
    # a build installed via Android Studio or a prior signing key - `install -r` alone fails
    # with INSTALL_FAILED_UPDATE_INCOMPATIBLE in that case. Uninstalling first sidesteps that
    # at the cost of wiping the app's local data/settings on the device every run.
    # Pass -KeepExistingInstall to skip this and just try a normal -r install instead.
    & $adb uninstall $ApplicationId | Out-Null
}
& $adb install -r $apk
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nInstalled successfully." -ForegroundColor Green
} else {
    Write-Host "`nadb install reported exit code $LASTEXITCODE - check the 'adb devices' output above for whether a device was actually listed." -ForegroundColor Yellow
}
