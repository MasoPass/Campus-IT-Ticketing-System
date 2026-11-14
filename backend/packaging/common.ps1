# Shared helpers for run.ps1 and build-installer.ps1 (Windows PowerShell 5.1+).
# Everything downloaded here goes into backend\.tools (git-ignored) and is reused.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # makes Invoke-WebRequest much faster
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$BackendDir = Split-Path -Parent $PSScriptRoot
$ToolsDir = Join-Path $BackendDir ".tools"
$MavenVersion = "3.9.9"

function Assert-Java21 {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Host "Java was not found. Install JDK 21:  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Red
        Write-Host "Then open a NEW PowerShell window." -ForegroundColor Red
        exit 1
    }
    $versionLine = (cmd /c "java -version 2>&1" | Select-Object -First 1) | Out-String
    if ($versionLine -match 'version "(\d+)(\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1) { $major = [int]$Matches[3] }
        if ($major -lt 21) {
            Write-Host "Found Java $major, but CampusDesk needs Java 21 or newer." -ForegroundColor Red
            exit 1
        }
        Write-Host "Java $major found." -ForegroundColor Green
    }
}

# The folder Java is installed in (works even when JAVA_HOME isn't set).
function Get-JavaHome {
    $line = cmd /c "java -XshowSettings:properties -version 2>&1" | Select-String "java.home" | Select-Object -First 1
    return $line.ToString().Split("=", 2)[1].Trim()
}

function Get-Maven {
    $mavenHome = Join-Path $ToolsDir "apache-maven-$MavenVersion"
    $mvn = Join-Path $mavenHome "bin\mvn.cmd"
    if (-not (Test-Path $mvn)) {
        Write-Host "Downloading Maven $MavenVersion (one time only)..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
        $zip = Join-Path $ToolsDir "maven.zip"
        Invoke-WebRequest -UseBasicParsing -OutFile $zip `
            -Uri "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
        Expand-Archive -Path $zip -DestinationPath $ToolsDir -Force
        Remove-Item $zip
    }
    return $mvn
}
