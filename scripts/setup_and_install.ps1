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

.PARAMETER ToolsDir
    Where the JDK-independent tooling (Gradle, Android SDK) is installed. Defaults to
    a folder in the user's profile, kept outside this repo so it isn't accidentally committed.

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
    [string]$ToolsDir = "$env:USERPROFILE\android-tools",
    [string]$JdkWingetId = "EclipseAdoptium.Temurin.21.JDK",
    [string]$GradleVersion = "9.3.1",
    [string]$MinFreeGb = 6,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

function Assert-FreeSpace([double]$minGb) {
    $freeGb = (Get-PSDrive C).Free / 1GB
    if ($freeGb -lt $minGb) {
        Write-Error ("Only {0:N2} GB free on C:. Need at least {1} GB free before this can run reliably (JDK + Gradle + Android SDK + dependency caches + build output all add up). Free up space and re-run." -f $freeGb, $minGb)
        exit 1
    }
    Write-Host ("Free disk space: {0:N2} GB (OK)" -f $freeGb)
}

# ---------------------------------------------------------------------------
# 0. Disk space guard - this exact script run out of space mid-build once;
#    fail fast and clearly instead of dying halfway through a multi-GB setup.
# ---------------------------------------------------------------------------
Write-Step "Checking free disk space"
Assert-FreeSpace $MinFreeGb

# ---------------------------------------------------------------------------
# 1. JDK 21
# ---------------------------------------------------------------------------
Write-Step "Locating / installing JDK 21"
$jdkRoot = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $jdkRoot) {
    Write-Host "JDK 21 not found - installing via winget..."
    winget install --id $JdkWingetId --silent --accept-package-agreements --accept-source-agreements
    $jdkRoot = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jdkRoot) { Write-Error "JDK install did not produce the expected directory under 'C:\Program Files\Eclipse Adoptium'."; exit 1 }
}
$env:JAVA_HOME = $jdkRoot.FullName
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
Write-Host "Android SDK at $sdkRoot"

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
Assert-FreeSpace $MinFreeGb
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
& $adb install -r $apk
Write-Host "`nDone. If no device was listed above, connect it and re-run this script (it will skip straight to the build/install steps)." -ForegroundColor Green
