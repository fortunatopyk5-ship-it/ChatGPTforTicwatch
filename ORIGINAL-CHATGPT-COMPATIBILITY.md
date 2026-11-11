# Original ChatGPT on TicWatch (ADB compatibility experiment)

**This is not a redesigned APK.** It only uses the developer-signed original ChatGPT Android app, and allows changing the watch's **global** display density and font size to reduce phone-UI clipping on the round screen. Whether login and keyboard work depends on the original app.

Latest source listed on APKMirror (Nov 10, 2026): https://www.apkmirror.com/apk/openai/chatgpt/chatgpt-1-2026-307-release/chatgpt-1-2026-307-7-android-apk-download/

## On Windows with already-connected watch (C:\\adb\\adb.exe)

1. Save the PowerShell helper as `C:\\adb\\ChatGPT_TicWatch_Original_Experimental.ps1`.
2. In PowerShell, run:
```powershell
cd C:\adb
.\ChatGPT_TicWatch_Original_Experimental.ps1 -Mode Status
```
3. If you already have original ChatGPT on the watch:
```powershell
.\ChatGPT_TicWatch_Original_Experimental.ps1 -Mode Apply -Density 240
```
4. Otherwise, after downloading the original APK/APKM bundle yourself to your PC:
```powershell
.\ChatGPT_TicWatch_Original_Experimental.ps1 -Mode InstallAndApply -SourceApk "C:\Users\YourUser\Downloads\chatgpt.apkm"
```
5. Restore defaults any time:
```powershell
.\ChatGPT_TicWatch_Original_Experimental.ps1 -Mode Restore
```

The helper will not modify/sign/repackage the app. It cannot fix layouts compiled into ChatGPT's source and does not make an official Wear OS build. On Wear OS Google sign-in may still be unavailable.

**Safety:** no credentials requested, no third-party proxy. Review the script before running. Do not trust a re-signed APK claiming to be official.

Source of this README is experimental; no real watch hardware test has been run.
