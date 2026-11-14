; Inno Setup script for CampusDesk. Compiled by build-installer.ps1, which passes:
;   /DAppVersion=1.0.0  /DSourceDir=<app-image folder>  /DOutputDir=<dist>  /DIconFile=<.ico>
; Produces CampusDesk-Setup-<version>.exe: installs per-user (no admin prompt), adds a
; Start menu entry (so Windows search finds it), optional desktop icon and an uninstaller.

[Setup]
AppId={{B3E2A7C4-5D19-4F6A-8C21-7E9D3A1F0B56}
AppName=CampusDesk
AppVersion={#AppVersion}
AppVerName=CampusDesk {#AppVersion}
AppPublisher=CampusDesk
DefaultDirName={localappdata}\Programs\CampusDesk
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename=CampusDesk-Setup-{#AppVersion}
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\CampusDesk.exe
UninstallDisplayName=CampusDesk
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"
Name: "startup"; Description: "Start CampusDesk when I sign in to Windows"; GroupDescription: "Options:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\CampusDesk"; Filename: "{app}\CampusDesk.exe"
Name: "{autodesktop}\CampusDesk"; Filename: "{app}\CampusDesk.exe"; Tasks: desktopicon
Name: "{userstartup}\CampusDesk"; Filename: "{app}\CampusDesk.exe"; Tasks: startup

[Run]
Filename: "{app}\CampusDesk.exe"; Description: "Open CampusDesk now"; Flags: nowait postinstall skipifsilent
