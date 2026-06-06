# Photos

A WiFi-managed photo frame for Meta Portal touch devices. Upload photos from any browser on your local network, then enjoy a full-screen slideshow on the Portal.

Based on [MrOplus/DigitalFrame](https://github.com/MrOplus/DigitalFrame) (MIT License). See [THIRD_PARTY_LICENSES-DigitalFrame.txt](THIRD_PARTY_LICENSES-DigitalFrame.txt).

## Prerequisites

- JDK 17
- Android SDK (command-line tools or Android Studio)
- Portal with USB debugging enabled, connected via USB-C
- Portal and upload device on the same WiFi network

## Build & Deploy

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # if needed
./gradlew assembleDebug
hzdb app install app/build/outputs/apk/debug/app-debug.apk --replace
hzdb app launch com.example.portalphotoframe
```

## Usage

1. Launch the app on your Portal.
2. Note the upload URL shown on screen (e.g. `http://192.168.x.x:8080`).
3. On your phone or laptop (same WiFi), open that URL in a browser.
4. Drag and drop photos to upload, or use the file picker.
5. Tap the Portal screen to show playback controls (prev / play-pause / next / settings).

## Settings

Open settings from the on-screen controls overlay to adjust:

- Slide duration (3s to 5min)
- Transition effect (crossfade, slide, none)
- Shuffle order

The screen follows the Portal's normal sleep timeout (display blacks out). When it wakes again, Photos returns to the foreground automatically unless you left the app intentionally (Home, another app).

## Portal constraints

- **No Google Mobile Services** — Maps, Firebase, Play Services, and GMS font downloads will not work.
- **SDK versions** — `minSdk 28`, `targetSdk 29` for maximum Portal compatibility.
- **Dark theme** — Portal's system overlay is white; this app uses a dark theme by default.
- **Top inset** — Overlays and settings reserve 64dp at the top for the system overlay strip.
- **Touch targets** — Interactive controls are at least 52dp with 16dp spacing.
- **Launcher icon** — Use a **512×512px PNG** in `mipmap-xxxhdpi/ic_launcher.png` only. Do not add `mipmap-mdpi` or other density buckets; Portal runs at 160dpi and Android will pick the tiny mdpi asset instead of your high-res icon. Declare `android:icon` on your launcher activity. Adaptive icons are not supported.

## Project structure

- `app/src/main/java/com/example/portalphotoframe/MainActivity.kt` — slideshow display
- `app/src/main/java/com/example/portalphotoframe/WebServerService.kt` — NanoHTTPD web server and REST API
- `app/src/main/java/com/example/portalphotoframe/ImageManager.kt` — local storage, thumbnails, EXIF
- `app/src/main/assets/web/index.html` — browser upload UI
- `design/photo-viewer.png` — source launcher artwork (used as-is; transparency preserved)
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` — 512×512 Portal launcher icon (regenerate via `python3 scripts/generate_launcher_icons.py`)

## Reference

- [Portal development documentation](https://developers.meta.com/horizon/documentation/android-apps/portal-development/)
- [portal-samples](https://github.com/meta-quest/portal-samples)
