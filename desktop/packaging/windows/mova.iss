; Instalador do MOVA para Windows (Inno Setup 6).
;
; Compilado no CI a partir do app-image do Compose/jpackage:
;   ISCC /DAppVersion=0.2.0 /DAppImage=<pasta MOVA> /O<saída> /F<nome> mova.iss
;
; - Instala só para o usuário, sem pedir administrador ({localappdata}\Programs\MOVA).
; - Idioma do instalador conforme o Windows (português, inglês, espanhol, francês).
; - AppId fixo: versões novas instalam por cima e o MOVA aberto é fechado antes.
; - Tela final com "Abrir o MOVA".

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef AppImage
  #error "Informe /DAppImage=<pasta do app-image MOVA>"
#endif

[Setup]
AppId={{2F0B6C43-9B7E-4C1E-8B0D-6A1F3B7C9E21}
AppName=MOVA
AppVersion={#AppVersion}
AppVerName=MOVA {#AppVersion}
AppPublisher=MOVA
DefaultDirName={localappdata}\Programs\MOVA
DefaultGroupName=MOVA
DisableProgramGroupPage=yes
DisableDirPage=yes
DisableReadyPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=..\mova.ico
UninstallDisplayIcon={app}\MOVA.exe
UninstallDisplayName=MOVA
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=force
RestartApplications=no
ShowLanguageDialog=no
LanguageDetectionMethod=uilanguage

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "pt"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\MOVA"; Filename: "{app}\MOVA.exe"
Name: "{autodesktop}\MOVA"; Filename: "{app}\MOVA.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\MOVA.exe"; Description: "{cm:LaunchProgram,MOVA}"; Flags: nowait postinstall skipifsilent

[InstallDelete]
; Cache de inicialização de uma versão anterior (é recriado na primeira execução).
Type: files; Name: "{app}\app\mova.jsa"

[UninstallDelete]
Type: files; Name: "{app}\app\mova.jsa"
