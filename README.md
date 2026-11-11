# WristGPT Bridge — TicWatch Pro 5 (EXPERIMENTAL)

A small, watch-shaped Wear OS app with **real keyboard text input**. This is **not** the official ChatGPT app and does not use an OpenAI API key.

It opens the separately installed, unmodified `com.openai.chatgpt` app, and tries to enter and read text using a narrowly scoped Accessibility Service. **It requires ChatGPT itself to work and already be signed in on the watch**; phone ChatGPT and Google sign-in may be incompatible with Wear OS. Not tested on a real TicWatch. UI automation can fail after ChatGPT updates.

### Build APK without Android Studio

1. Open **Actions > Build Wear OS APK** in this repository.
2. Open a successful run, then download its **WristGPT-Bridge-APK** artifact.
3. Unzip to get `app-debug.apk`. Sideload using ADB wireless debugging, e.g. via a phone ADB app or PC command-line ADB.

### On watch

Install the ORIGINAL ChatGPT Android app separately (no modified APKs). Confirm Google sign-in works **in that original app**, not in WristGPT. Install the WristGPT APK, go to Android Settings > Accessibility and enable `WristGPT - ChatGPT UI`, then open WristGPT and type using the watch keyboard. Tap Send.

### Security and limits

- No INTERNET permission. No passwords, OAuth tokens or API keys.
- Accessibility is restricted to UI events from `com.openai.chatgpt`, but this is still a powerful permission; review the source.
- Local messages are stored unencrypted in app-private preferences. Do not use for sensitive conversations.
- The bridge may fail to find the input/send button or may capture incomplete/unrelated visible text. It cannot guarantee Google login works.
- This is a source prototype and not a production or officially supported ChatGPT client.