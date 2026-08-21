# Sect Master — Native Android Game

Sect Master is an offline sect-management game built entirely with native Android APIs. Build a mountain sanctuary, recruit and train disciples, develop an economy, research permanent teachings, and send your roster on expeditions.

## Native Android edition

- Java and Android Canvas/View input and rendering
- No WebView, HTML, JavaScript, browser runtime, network access, ads, or analytics
- English-only player interface
- Responsive landscape layout with touch-friendly controls
- Versioned, validated `SharedPreferences` save data
- Automatic saves, lifecycle saves, and capped eight-hour offline production
- Defensive simulation limits to prevent time jumps, invalid resources, duplicate placement, and corrupted saves
- No sensitive Android permissions

## Requirements

- Android Studio with JDK 17
- Android SDK 35
- Android 6.0 (API 23) or newer device

Open the `android/` directory in Android Studio, or build from a terminal with Gradle 8.9:

```bash
cd android
gradle lintDebug assembleDebug
```

The debug APK is written under `android/app/build/outputs/apk/debug/`.

## Play Store bundle

The release configuration targets API 35 and produces an Android App Bundle:

```bash
cd android
gradle bundleRelease
```

Supply release signing values outside Git (for example, in `~/.gradle/gradle.properties`):

```properties
RELEASE_STORE_FILE=/absolute/path/upload-key.jks
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=your-key-password
```

Never commit a keystore or signing credentials. Google Play App Signing is recommended. Before publishing, provide store listing artwork, screenshots, content-rating answers, a privacy-policy URL if required by your account, and complete Play Console testing requirements.

The GitHub Actions workflow runs lint, creates a debug APK, and creates an unsigned release AAB for signing and upload.

## Project layout

```text
android/app/src/main/java/com/sectmaster/game/
  MainActivity.java   Activity lifecycle, immersive mode, reset confirmation
  GameView.java       Native rendering, touch controls, and all game screens
  GameState.java      Simulation, economy, progression, and persistence
android/app/src/main/res/                  Android resources
android/build-android.yml                  CI build and Play bundle template
```
