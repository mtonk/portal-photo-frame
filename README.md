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
- **Scheduled quiet hours** — outside quiet hours (default 11:00 PM–7:00 AM when enabled), Photos keeps the screen on so Portal Ambient does not take over. During quiet hours the Portal may sleep or show Ambient normally. When quiet hours end, keep-alive resumes passively the next time Photos is on screen (no automatic launch or wake).

With quiet hours off, the app does not override Portal display timeouts. Configure screen sleep and Ambient on the Portal itself.

## Portal constraints

- **No Google Mobile Services** — Maps, Firebase, and Play Services will not work. Inter is bundled locally (portal-samples uses GMS downloadable fonts, which are unavailable on Portal).
- **SDK versions** — `minSdk 28`, `targetSdk 29` for maximum Portal compatibility.
- **Portal design system** — Bundled Inter typeface (same weights as [portal-samples](https://github.com/meta-quest/portal-samples)), Meta palette (`#1A1A1A` / `#2B2B2B` / `#0866FF` / `#DADADA`), 52dp touch targets, and 16dp spacing per [design requirements](https://developers.meta.com/horizon/documentation/android-apps/portal-design-requirements/).
- **Top inset** — Overlays and settings reserve 64dp at the top for the system overlay strip. The slideshow stays immersive fullscreen (overlay hidden until edge swipe).
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
