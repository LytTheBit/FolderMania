# Builds the Windows installer: build/jpackage/output/Folder Mania-<version>.exe
# Usage: .\build-installer.ps1 [-Msi]
param([switch]$Msi)

if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $wix = Get-ChildItem 'C:\Program Files (x86)\WiX Toolset v3*\bin' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $wix) { throw 'WiX Toolset 3.x not found. Install it: winget install WiXToolset.WiXToolset' }
    $env:PATH = "$($wix.FullName);$env:PATH"
}

# Avoids "Unable to establish loopback connection" when TEMP has an unusual path
$sock = 'C:\Temp\jdk'
New-Item -ItemType Directory -Force $sock | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$sock"

$type = if ($Msi) { 'msi' } else { 'exe' }
& "$PSScriptRoot\gradlew.bat" -p $PSScriptRoot packageInstaller "-PinstallerType=$type"
if ($LASTEXITCODE) { exit $LASTEXITCODE }
Write-Host "Done: see build\jpackage\output (or %LOCALAPPDATA%\FolderMania-build if the project is on OneDrive)"
