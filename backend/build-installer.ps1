# Builds the Windows app.
#
#   powershell -ExecutionPolicy Bypass -File .\build-installer.ps1
#
# Always produces:
#   target\dist\CampusDesk\CampusDesk.exe        - the app (bundled Java runtime)
#   target\dist\CampusDesk-1.0.0-portable.zip    - same folder zipped, for GitHub releases
# And, if Inno Setup is installed (winget install JRSoftware.InnoSetup):
#   target\dist\CampusDesk-Setup-1.0.0.exe       - a real installer (Start menu, desktop icon, uninstaller)
#
# jpackage (ships with the JDK) builds the app: it bundles a trimmed Java runtime made by
# jlink, our Spring Boot jar, and a native CampusDesk.exe launcher.

param([switch]$SkipTests)

. (Join-Path $PSScriptRoot "packaging\common.ps1")
Set-Location $PSScriptRoot

$AppVersion = "1.0.0"

Assert-Java21
$javaHome = Get-JavaHome
$jpackage = Join-Path $javaHome "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) { throw "jpackage not found in $javaHome - use a full JDK 21, not a JRE." }
$mvn = Get-Maven

# 1. Build the Spring Boot jar ------------------------------------------------------
Write-Host "Building campusdesk.jar..." -ForegroundColor Cyan
[string[]]$buildArgs = @("-q", "clean", "package")
if ($SkipTests) { $buildArgs += "-DskipTests" }
& $mvn @buildArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

# 2. Stage what goes into the app ------------------------------------------------------
$stage = Join-Path $PSScriptRoot "target\installer-input"
$dist = Join-Path $PSScriptRoot "target\dist"
foreach ($d in @($stage, $dist)) { if (Test-Path $d) { Remove-Item -Recurse -Force $d } }
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item (Join-Path $PSScriptRoot "target\campusdesk.jar") $stage

# 3. App image (CampusDesk.exe + runtime + jar) ----------------------------------------
Write-Host "Packaging app with jpackage (bundles a Java runtime, takes a minute)..." -ForegroundColor Cyan
& $jpackage --type app-image `
    --name CampusDesk `
    --app-version $AppVersion `
    --vendor "CampusDesk" `
    --description "Campus IT help desk" `
    --input $stage `
    --main-jar campusdesk.jar `
    --icon (Join-Path $PSScriptRoot "packaging\campusdesk.ico") `
    --java-options '-Dcampusdesk.desktop=true' `
    --java-options '-Xmx512m' `
    --dest $dist
if ($LASTEXITCODE -ne 0) { throw "jpackage (app-image) failed" }

$zip = Join-Path $dist "CampusDesk-$AppVersion-portable.zip"
Compress-Archive -Path (Join-Path $dist "CampusDesk") -DestinationPath $zip -Force
Write-Host "App:      $dist\CampusDesk\CampusDesk.exe" -ForegroundColor Green
Write-Host "Portable: $zip" -ForegroundColor Green

# 4. Installer (CampusDesk-Setup-x.y.z.exe) via Inno Setup ----------------------------
$iscc = $null
$onPath = Get-Command ISCC.exe -ErrorAction SilentlyContinue
if ($onPath) { $iscc = $onPath.Source }
foreach ($c in @(
        (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
        (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"))) {
    if (-not $iscc -and $c -and (Test-Path $c)) { $iscc = $c }
}

if ($iscc) {
    Write-Host "Building installer with Inno Setup..." -ForegroundColor Cyan
    & $iscc /Q `
        "/DAppVersion=$AppVersion" `
        "/DSourceDir=$(Join-Path $dist 'CampusDesk')" `
        "/DOutputDir=$dist" `
        "/DIconFile=$(Join-Path $PSScriptRoot 'packaging\campusdesk.ico')" `
        (Join-Path $PSScriptRoot "packaging\campusdesk.iss")
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed" }
    Write-Host "Installer: $dist\CampusDesk-Setup-$AppVersion.exe" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "Inno Setup not found, so only the app folder + portable zip were built." -ForegroundColor Yellow
    Write-Host "For CampusDesk-Setup.exe, run once:  winget install JRSoftware.InnoSetup" -ForegroundColor Yellow
    Write-Host "then open a NEW PowerShell window and run this script again." -ForegroundColor Yellow
}
