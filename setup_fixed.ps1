# ============================================================
#  setup.ps1  --  One-click installer for Ops Transfer Tool
#  Run as Administrator (right-click -> Run with PowerShell)
#  Tested on Windows 10 / Windows 11 / Windows Server 2019+
#
#  Prerequisites (place in the same folder as this script):
#    OpsTransferTool-source.zip  -- Java source files
#
#  This script will:
#    1. Check / install Temurin JDK 21 (includes javac)
#    2. Check / install WinSCP
#    3. Extract source zip, compile, and build OpsTransferTool.jar
#    4. Deploy to C:\OpsTools
#    5. Create Desktop shortcut + Start Menu entry (all users)
# ============================================================

#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"

# -- Config -------------------------------------------------------------------
$InstallDir   = "C:\OpsTools"
$JarName      = "OpsTransferTool.jar"
$SourceZip    = Join-Path $PSScriptRoot "OpsTransferTool-source.zip"
$BuildDir     = "$env:TEMP\opstool-build"

# Temurin JDK 21 LTS -- full JDK (includes javac)
$JavaUrl      = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.3_9.msi"
$JavaMsi      = "$env:TEMP\OpenJDK21-jdk.msi"

$WinScpUrl    = "https://winscp.net/download/WinSCP-6.3.3-Setup.exe"
$WinScpExe    = "$env:TEMP\WinSCP-Setup.exe"
$WinScpCom    = "C:\Program Files (x86)\WinSCP\WinSCP.com"
$WinScpCom64  = "C:\Program Files\WinSCP\WinSCP.com"

$ShortcutName = "Ops Transfer Tool"
$LogFile      = "$env:TEMP\opstool-setup.log"

# -- Helpers ------------------------------------------------------------------

function Log($msg) {
    $line = "$(Get-Date -Format 'HH:mm:ss')  $msg"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}
function Step($msg) {
    Write-Host ""
    Write-Host "------------------------------------------" -ForegroundColor DarkGray
    Write-Host "  $msg" -ForegroundColor Cyan
    Write-Host "------------------------------------------" -ForegroundColor DarkGray
}
function OK($msg)   { Write-Host "  v  $msg" -ForegroundColor Green }
function SKIP($msg) { Write-Host "  -  $msg" -ForegroundColor Yellow }
function FAIL($msg) {
    Write-Host ""
    Write-Host "  x  ERROR: $msg" -ForegroundColor Red
    Write-Host "     See log: $LogFile"
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}
function Download($url, $dest, $label) {
    Log "Downloading $label ..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
        OK "Downloaded $label"
    } catch {
        FAIL "Could not download ${label}. Check internet connection.`n     $_"
    }
}

# -- Java JDK discovery (needs javac, not just javaw) -------------------------

function Find-JavaBin {
    # Returns the bin\ folder containing java.exe + javac.exe

    # 1. JAVA_HOME
    if ($env:JAVA_HOME) {
        if (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe")) {
            return (Join-Path $env:JAVA_HOME "bin")
        }
    }

    # 2. PATH
    $jc = Get-Command javac -ErrorAction SilentlyContinue
    if ($jc) { return (Split-Path $jc.Source) }

    # 3. Common install roots
    $roots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Java",
        "C:\Program Files (x86)\Java"
    )
    foreach ($root in $roots) {
        if (Test-Path $root) {
            $found = Get-ChildItem "$root\*\bin\javac.exe" -ErrorAction SilentlyContinue |
                     Select-Object -First 1
            if ($found) { return $found.DirectoryName }
        }
    }

    # 4. Registry
    foreach ($rp in @("HKLM:\SOFTWARE\JavaSoft\JDK",
                       "HKLM:\SOFTWARE\Eclipse Adoptium\JDK",
                       "HKLM:\SOFTWARE\JavaSoft\Java Development Kit")) {
        if (Test-Path $rp) {
            try {
                $ver  = (Get-ItemProperty $rp).CurrentVersion
                $home = (Get-ItemProperty "$rp\$ver").JavaHome
                if (Test-Path (Join-Path $home "bin\javac.exe")) {
                    return (Join-Path $home "bin")
                }
            } catch {}
        }
    }
    return $null
}

function Get-JavaMajorVersion($binDir) {
    try {
        $out = & "$binDir\java.exe" -version 2>&1
        $ver = ($out | Select-String "version").ToString()
        if ($ver -match '"(\d+)') { return [int]$Matches[1] }
    } catch {}
    return 0
}

# -- Banner --------------------------------------------------------------------
Clear-Host
Write-Host ""
Write-Host "  +======================================================+" -ForegroundColor Cyan
Write-Host "  |       Ops Transfer Tool  --  One-Click Setup         |" -ForegroundColor Cyan
Write-Host "  +======================================================+" -ForegroundColor Cyan
Write-Host ""
Write-Host "  This script will:"
Write-Host "    1. Check / install Java JDK 21 (Temurin LTS)"
Write-Host "    2. Check / install WinSCP"
Write-Host "    3. Compile source zip -> OpsTransferTool.jar"
Write-Host "    4. Deploy to $InstallDir"
Write-Host "    5. Create Desktop shortcut + Start Menu entry (all users)"
Write-Host "    6. Register background daemon (Windows Task Scheduler)"
Write-Host ""
Write-Host "  Log: $LogFile"
Write-Host ""
$confirm = Read-Host "Continue? [Y/n]"
if ($confirm -match "^[Nn]") { exit 0 }

# -- 1. Locate source zip -----------------------------------------------------
Step "Locating source zip"

if (-not (Test-Path $SourceZip)) {
    # Also try current working directory
    $SourceZip = Join-Path (Get-Location) "OpsTransferTool-source.zip"
}
if (-not (Test-Path $SourceZip)) {
    FAIL "OpsTransferTool-source.zip not found.`n     Place it in the same folder as setup.ps1 and re-run."
}
OK "Found: $SourceZip"

# -- 2. Java JDK --------------------------------------------------------------
Step "Checking Java JDK (javac required for compilation)"

$javaBin = Find-JavaBin

if ($javaBin) {
    $ver = Get-JavaMajorVersion $javaBin
    if ($ver -ge 11) {
        OK "JDK $ver found: $javaBin"
    } else {
        Log "JDK $ver is too old (need 11+). Installing Temurin JDK 21..."
        $javaBin = $null
    }
}

if (-not $javaBin) {
    Download $JavaUrl $JavaMsi "Temurin JDK 21"
    Log "Installing JDK silently (this may take a minute)..."
    $msiArgs = @("/i", $JavaMsi, "/quiet", "/norestart",
                 "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome",
                 "/L*V", "$env:TEMP\jdk-install.log")
    $proc = Start-Process "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru
    if ($proc.ExitCode -notin @(0, 3010)) {
        FAIL "JDK installer exited with code $($proc.ExitCode). See $env:TEMP\jdk-install.log"
    }
    Remove-Item $JavaMsi -Force -ErrorAction SilentlyContinue

    # Refresh PATH
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("PATH","User")

    $javaBin = Find-JavaBin
    if (-not $javaBin) {
        FAIL "JDK installed but javac.exe not found. Restart this machine and re-run setup."
    }
    OK "JDK installed: $javaBin"
}

$JavacExe = Join-Path $javaBin "javac.exe"
$JavaExe  = Join-Path $javaBin "java.exe"
$JavawExe = Join-Path $javaBin "javaw.exe"
if (-not (Test-Path $JavawExe)) { $JavawExe = $JavaExe }   # server JDK may not have javaw

# -- 3. WinSCP ----------------------------------------------------------------
Step "Checking WinSCP"

$resolvedWinScp = $null
if (Test-Path $WinScpCom)   { $resolvedWinScp = $WinScpCom }
if (Test-Path $WinScpCom64) { $resolvedWinScp = $WinScpCom64 }
if (-not $resolvedWinScp) {
    $ws = Get-Command "WinSCP.com" -ErrorAction SilentlyContinue
    if ($ws) { $resolvedWinScp = $ws.Source }
}

if ($resolvedWinScp) {
    OK "WinSCP already installed: $resolvedWinScp"
} else {
    Download $WinScpUrl $WinScpExe "WinSCP 6.3.3"
    Log "Installing WinSCP silently..."
    $proc = Start-Process $WinScpExe -ArgumentList "/VERYSILENT /NORESTART /ALLUSERS" -Wait -PassThru
    if ($proc.ExitCode -notin @(0, 3010)) {
        FAIL "WinSCP installer exited with code $($proc.ExitCode)."
    }
    Remove-Item $WinScpExe -Force -ErrorAction SilentlyContinue

    if      (Test-Path $WinScpCom)   { $resolvedWinScp = $WinScpCom }
    elseif  (Test-Path $WinScpCom64) { $resolvedWinScp = $WinScpCom64 }
    else    { FAIL "WinSCP installed but WinSCP.com not found. Check C:\Program Files." }

    OK "WinSCP installed: $resolvedWinScp"
}

# -- 4. Extract source zip ----------------------------------------------------
Step "Extracting source zip"

if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
New-Item -ItemType Directory -Path $BuildDir | Out-Null

$SrcDir     = Join-Path $BuildDir "src"
$ClassesDir = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Path $SrcDir     | Out-Null
New-Item -ItemType Directory -Path $ClassesDir | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($SourceZip, $SrcDir)

$javaFiles = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse
if ($javaFiles.Count -eq 0) {
    FAIL "No .java files found inside OpsTransferTool-source.zip. Check the zip contents."
}
OK "Extracted $($javaFiles.Count) source files"

# -- 5. Compile ---------------------------------------------------------------
Step "Compiling Java source"

# Write source file list to avoid command-line length limits
$fileListPath = Join-Path $BuildDir "sources.txt"
$javaFiles.FullName | Set-Content $fileListPath -Encoding UTF8

$compileArgs = @("-d", $ClassesDir, "-source", "11", "-target", "11", "-encoding", "UTF-8",
                 "@$fileListPath")
$proc = Start-Process $JavacExe -ArgumentList $compileArgs -Wait -PassThru `
        -RedirectStandardError  "$BuildDir\javac-errors.txt" `
        -RedirectStandardOutput "$BuildDir\javac-out.txt" `
        -WindowStyle Hidden

if ($proc.ExitCode -ne 0) {
    $errText = Get-Content "$BuildDir\javac-errors.txt" -Raw -ErrorAction SilentlyContinue
    Log "Compilation errors:`n$errText"
    FAIL "Compilation failed. Details:`n$errText"
}
OK "Compilation successful"

# -- 6. Build JAR ------------------------------------------------------------
Step "Building JAR"

$manifestDir  = Join-Path $BuildDir "manifest"
New-Item -ItemType Directory -Path $manifestDir | Out-Null
$manifestFile = Join-Path $manifestDir "MANIFEST.MF"

# MANIFEST.MF -- trailing newline is required by the JAR spec
@"
Manifest-Version: 1.0
Main-Class: com.opstool.App

"@ | Set-Content $manifestFile -Encoding ASCII

$JarExe  = Join-Path $javaBin "jar.exe"
$builtJar = Join-Path $BuildDir $JarName

Push-Location $ClassesDir
$jarArgs = @("cfm", $builtJar, $manifestFile, ".")
$proc = Start-Process $JarExe -ArgumentList $jarArgs -Wait -PassThru `
        -RedirectStandardError "$BuildDir\jar-errors.txt" `
        -WindowStyle Hidden
Pop-Location

if ($proc.ExitCode -ne 0) {
    $errText = Get-Content "$BuildDir\jar-errors.txt" -Raw -ErrorAction SilentlyContinue
    FAIL "JAR creation failed.`n$errText"
}
OK "JAR built: $builtJar"

# -- 7. Deploy ----------------------------------------------------------------
Step "Deploying to $InstallDir"

if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
    OK "Created: $InstallDir"
} else {
    SKIP "Folder exists: $InstallDir"
}

$finalJar = Join-Path $InstallDir $JarName
Copy-Item $builtJar $finalJar -Force
OK "Deployed JAR: $finalJar"

# Create data directory for credentials and tasks
$DataDir = Join-Path $env:USERPROFILE ".opstool"
if (-not (Test-Path $DataDir)) {
    New-Item -ItemType Directory -Path $DataDir | Out-Null
    OK "Created data directory: $DataDir"
} else {
    SKIP "Data directory exists: $DataDir"
}

# launch.bat fallback
@"
@echo off
cd /d "%~dp0"
start "" "$JavawExe" -jar "$finalJar"
"@ | Set-Content (Join-Path $InstallDir "launch.bat") -Encoding ASCII
OK "Created launch.bat"

# Clean up build temp
Remove-Item $BuildDir -Recurse -Force -ErrorAction SilentlyContinue
Log "Build temp cleaned up"

# -- 8. Shortcuts -------------------------------------------------------------
Step "Creating shortcuts"

$WshShell = New-Object -ComObject WScript.Shell

# Desktop (all users)
$desktopPath = [Environment]::GetFolderPath("CommonDesktopDirectory")
$desktopLink = Join-Path $desktopPath "$ShortcutName.lnk"
$sc = $WshShell.CreateShortcut($desktopLink)
$sc.TargetPath       = $JavawExe
$sc.Arguments        = "-jar `"$finalJar`""
$sc.WorkingDirectory = $InstallDir
$sc.Description      = "Ops Transfer & Task Manager"
$sc.WindowStyle      = 1
$sc.Save()
OK "Desktop shortcut: $desktopLink"

# Start Menu (all users)
$startDir  = Join-Path ([Environment]::GetFolderPath("CommonPrograms")) "Ops Tools"
if (-not (Test-Path $startDir)) { New-Item -ItemType Directory -Path $startDir | Out-Null }
$startLink = Join-Path $startDir "$ShortcutName.lnk"
$sc2 = $WshShell.CreateShortcut($startLink)
$sc2.TargetPath       = $JavawExe
$sc2.Arguments        = "-jar `"$finalJar`""
$sc2.WorkingDirectory = $InstallDir
$sc2.Description      = "Ops Transfer & Task Manager"
$sc2.WindowStyle      = 1
$sc2.Save()
OK "Start Menu entry: $startLink"

# -- 9. Pre-configure WinSCP path in Java prefs -------------------------------
Step "Pre-configuring application preferences"

try {
    $regKey = "HKCU:\Software\JavaSoft\Prefs\com\opstool\ui\settings0panel"
    if (-not (Test-Path $regKey)) { New-Item -Path $regKey -Force | Out-Null }
    Set-ItemProperty -Path $regKey -Name "winscp_path" -Value $resolvedWinScp -Type String
    OK "WinSCP path saved to app preferences"
} catch {
    SKIP "Could not save preferences (non-critical, set manually in Settings tab): $_"
}

# -- 10. Register background daemon (Windows Task Scheduler) -----------------
Step "Registering background scheduler daemon"

$DaemonTaskName = "OpsTransferToolDaemon"
$DaemonDataDir  = "$env:USERPROFILE\.opstool"

try {
    Unregister-ScheduledTask -TaskName $DaemonTaskName -Confirm:$false -ErrorAction SilentlyContinue

    $action = New-ScheduledTaskAction `
        -Execute $JavawExe `
        -Argument "-cp `"$finalJar`" com.opstool.Daemon `"$DaemonDataDir`""

    $trigStart       = New-ScheduledTaskTrigger -AtStartup
    $trigStart.Delay = "PT1M"

    $trigHourly = New-ScheduledTaskTrigger -RepetitionInterval (New-TimeSpan -Minutes 60) `
                    -Once -At (Get-Date)

    $settings = New-ScheduledTaskSettingsSet `
        -ExecutionTimeLimit          ([TimeSpan]::Zero) `
        -RestartCount                3 `
        -RestartInterval             (New-TimeSpan -Minutes 5) `
        -MultipleInstances           IgnoreNew `
        -StartWhenAvailable          $true `
        -RunOnlyIfNetworkAvailable   $false

    Register-ScheduledTask `
        -TaskName  $DaemonTaskName `
        -Action    $action `
        -Trigger   $trigStart, $trigHourly `
        -Settings  $settings `
        -RunLevel  Highest `
        -User      "SYSTEM" `
        -Force | Out-Null

    OK "Daemon registered as Windows Scheduled Task: $DaemonTaskName"
    OK "Runs at startup + every 60 min, as SYSTEM (no login required)"

    Start-ScheduledTask -TaskName $DaemonTaskName -ErrorAction SilentlyContinue
    OK "Daemon started immediately for first run"

} catch {
    Write-Host ""
    Write-Host "  !  WARNING: Could not register daemon: $_" -ForegroundColor Yellow
    Write-Host "     Tasks still run while GUI is open." -ForegroundColor Yellow
    Write-Host "     Register manually via Settings tab after install." -ForegroundColor Yellow
}

# -- 11. Summary ---------------------------------------------------------------
Write-Host ""
Write-Host "  +======================================================+" -ForegroundColor Green
Write-Host "  |           Setup completed successfully!              |" -ForegroundColor Green
Write-Host "  +======================================================+" -ForegroundColor Green
Write-Host ""
Write-Host "  Installed to  : $InstallDir"     -ForegroundColor White
Write-Host "  Java          : $JavawExe"        -ForegroundColor White
Write-Host "  WinSCP        : $resolvedWinScp"  -ForegroundColor White
Write-Host "  JAR           : $finalJar"        -ForegroundColor White
Write-Host "  Data dir      : $DataDir"         -ForegroundColor White
Write-Host "  Desktop icon  : $ShortcutName"    -ForegroundColor White
Write-Host ""
Write-Host "  -- What ops team members need to do next -------------" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Double-click 'Ops Transfer Tool' on the desktop"
Write-Host "  2. Click 'New Task' -- enter destination folder,"
Write-Host "     target hostname, username and password"
Write-Host "  3. Set a schedule and click Create Task"
Write-Host ""
Write-Host "  Credentials are saved automatically in:"
Write-Host "  $DataDir\creds_<username>.xml  (plain text, one file per user)"
Write-Host ""
Write-Host "  -- One-time setup on each TARGET machine -------------" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Windows (file transfer target):"
Write-Host "    Settings > Apps > Optional Features > OpenSSH Server"
Write-Host "    Then (as admin): Start-Service sshd"
Write-Host "                     Set-Service sshd -StartupType Automatic"
Write-Host ""
Write-Host "  Windows (service control target):"
Write-Host "    Run as admin: Enable-PSRemoting -Force"
Write-Host ""
Write-Host "  Linux target:"
Write-Host "    sudo systemctl enable --now ssh"
Write-Host ""
Write-Host "  Log file: $LogFile"
Write-Host ""

$launch = Read-Host "Launch Ops Transfer Tool now? [Y/n]"
if ($launch -notmatch "^[Nn]") {
    Start-Process $JavawExe -ArgumentList "-jar `"$finalJar`""
    OK "Application launched."
}
