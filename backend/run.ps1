# CampusDesk dev launcher for Windows.
# Checks Java, fetches Maven the first time, then starts CampusDesk in desktop mode
# (its own window + tray icon, data in %LOCALAPPDATA%\CampusDesk).
#
#   powershell -ExecutionPolicy Bypass -File .\run.ps1           -> start the app
#   powershell -ExecutionPolicy Bypass -File .\run.ps1 test      -> run the tests

. (Join-Path $PSScriptRoot "packaging\common.ps1")
Set-Location $PSScriptRoot

Assert-Java21
$mvn = Get-Maven

# Spring Boot reads this environment variable as campusdesk.desktop.
$env:CAMPUSDESK_DESKTOP = "true"

# Build the argument list explicitly: an `if` expression would unwrap a one-item array
# into a plain string, which Maven then receives garbled.
[string[]]$mavenArgs = @("spring-boot:run")
if ($args.Count -gt 0) { $mavenArgs = [string[]]$args }

Write-Host "Starting CampusDesk... (close this window or use the tray icon > Quit to stop it)" -ForegroundColor Cyan
& $mvn @mavenArgs
exit $LASTEXITCODE
