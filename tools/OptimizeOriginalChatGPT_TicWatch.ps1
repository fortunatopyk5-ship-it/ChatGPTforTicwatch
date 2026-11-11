# ChatGPT on TicWatch: original unmodified OpenAI APK, no API, no Bridge, no root.
# This is NOT a full UI redesign. Changes GLOBAL watch display scale only.
param(
 [ValidateSet('Status','Apply','Restore')][string]$Mode='Status',
 [ValidateRange(180,350)][int]$Density=240,
 [string]$Adb='C:\adb\adb.exe'
)
$ErrorActionPreference='Stop'
if (!(Test-Path $Adb)) { throw 'ADB missing: check C:\adb\adb.exe' }
$devices=@(& $Adb devices | Where-Object { $_ -match '^[^\s]+\s+device\s*$' })
if($devices.Count -ne 1){ throw 'Connect exactly one TicWatch via ADB first; run .\adb.exe devices' }
function Cmd([string[]]$arguments) {
 & $Adb @arguments
 if($LASTEXITCODE -ne 0){ throw ('ADB failed: '+($arguments -join ' ')) }
}
$paths=@(& $Adb shell pm path com.openai.chatgpt)
$installed=($paths -match '^package:').Count -gt 0
if($Mode -eq 'Apply'){
 if(!$installed){throw 'Official ChatGPT app not installed on watch. Install original signed APK first.'}
 Cmd @('shell','wm','density',[string]$Density)
 Cmd @('shell','settings','put','system','font_scale','0.93')
 Cmd @('shell','am','force-stop','com.openai.chatgpt')
 Cmd @('shell','monkey','-p','com.openai.chatgpt','-c','android.intent.category.LAUNCHER','1')
 Write-Host 'Original ChatGPT launched. Layout scaling applied to ENTIRE watch; no app signature was changed.'
}
elseif($Mode -eq 'Restore'){
 Cmd @('shell','wm','density','reset')
 Cmd @('shell','settings','put','system','font_scale','1.0')
 Write-Host 'Restored.'
}
else {
 Cmd @('shell','wm','size')
 Cmd @('shell','wm','density')
 if($installed){Write-Host 'Original ChatGPT found.'}
 else{Write-Host 'Original ChatGPT not installed.'}
}
