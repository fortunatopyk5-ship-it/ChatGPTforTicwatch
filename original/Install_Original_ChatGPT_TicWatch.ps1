# Installs only the original, unmodified ChatGPT split APKs from the user's APKM file.
# Changing density affects the whole watch, reversible with -Mode Restore.
param(
    [ValidateSet('Install','Optimize','Launch','Restore','Status')][string]$Mode = 'Status',
    [ValidateRange(180,350)][int]$Density = 205,
    [string]$Apkm = '',
    [string]$Adb = 'C:\adb\adb.exe'
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path $Adb)) { throw "ADB not found at $Adb" }
function Invoke-ADB {
    param([string[]]$ArgsList)
    & $Adb @ArgsList
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($ArgsList -join ' ')" }
}
$online = @(& $Adb devices | Select-String '^\S+\s+device\s*$')
if ($online.Count -ne 1) { throw 'Connect exactly one TicWatch. Check adb devices.' }
$abi = (& $Adb shell getprop ro.product.cpu.abi).Trim()
$sdk = [int]((& $Adb shell getprop ro.build.version.sdk).Trim())
$initialDensity = (& $Adb shell wm density | Out-String).Trim()
Write-Host "Watch ABI=$abi API=$sdk; $initialDensity"
switch ($Mode) {
  'Status' { Invoke-ADB -ArgsList @('shell','pm','list','packages','com.openai.chatgpt'); exit 0 }
  'Restore' {
    Invoke-ADB -ArgsList @('shell','wm','density','reset')
    Invoke-ADB -ArgsList @('shell','settings','put','system','font_scale','1.0')
    Write-Host 'Restored watch display settings.'; exit 0
  }
  'Optimize' {
    Invoke-ADB -ArgsList @('shell','wm','density',"$Density")
    Invoke-ADB -ArgsList @('shell','settings','put','system','font_scale','0.94')
    Write-Host 'GLOBAL display density changed; use -Mode Restore to revert.'; exit 0
  }
  'Launch' {
    Invoke-ADB -ArgsList @('shell','monkey','-p','com.openai.chatgpt','-c','android.intent.category.LAUNCHER','1'); exit 0
  }
}
if ($sdk -lt 32) { throw 'This version of ChatGPT requires Android API 32+.' }
if (!$Apkm) {
    $candidate = @(Get-ChildItem -Path $PSScriptRoot -Filter '*.apkm' -File)
    if ($candidate.Count -ne 1) { throw 'Put the original .apkm file next to this script, or set -Apkm path' }
    $Apkm = $candidate[0].FullName
}
if (!(Test-Path $Apkm)) { throw "File not found: $Apkm" }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('openai-original-' + [guid]::NewGuid().ToString('N'))
[System.IO.Directory]::CreateDirectory($temp) | Out-Null
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path $Apkm).Path,$temp)
    $arch = switch ($abi) {
      'arm64-v8a' { 'arm64_v8a' }
      'armeabi-v7a' { 'armeabi_v7a' }
      'x86' { 'x86' }
      'x86_64' { 'x86_64' }
      default { throw "Unsupported CPU ABI: $abi" }
    }
    $dpi = [int]([regex]::Match($initialDensity,'Physical density:\s*(\d+)').Groups[1].Value)
    if ($dpi -le 0) { $dpi = 320 }
    $densityMap = @{'120'='ldpi';'160'='mdpi';'213'='tvdpi';'240'='hdpi';'320'='xhdpi';'480'='xxhdpi';'640'='xxxhdpi'}
    $chosen = @(120,160,213,240,320,480,640) | Sort-Object { [math]::Abs($_ - $dpi) } | Select-Object -First 1
    $densitySplit = "split_config.$($densityMap["$chosen"]).apk"
    $required = @('base.apk',"split_config.$arch.apk",$densitySplit,'split_config.en.apk','split_config.uk.apk')
    foreach ($file in $required) { if (!(Test-Path (Join-Path $temp $file))) { throw "Missing original APK split: $file" } }
    $installArgs = @('install-multiple','-r') + @($required | Select-Object -Unique | ForEach-Object { Join-Path $temp $_ })
    Invoke-ADB -ArgsList $installArgs
    Write-Host 'Original OpenAI-signed ChatGPT installed. No app modifications were made.'
    Write-Host 'Use -Mode Launch to open it; -Mode Optimize -Density 205 to try scaling; -Mode Restore to undo.'
}
finally { Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue }
