# Meal Tracker - Android APK

A self-contained Android WebView wrapper for Andrea's Meal Tracker app. All HTML/CSS/JS is bundled in the APK — no server required.

## Requirements

- **Android Studio** (Hedgehog 2023.1+ recommended) or Android SDK command-line tools
- **JDK 17** (bundled with Android Studio)
- **Android SDK 34** (install via SDK Manager)

## Build Instructions

### Option A: Android Studio
1. Open this folder as a project in Android Studio
2. Let Gradle sync complete
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Command Line
```bash
# Set ANDROID_HOME if not already set
set ANDROID_HOME=C:\Users\<user>\AppData\Local\Android\Sdk

# Generate gradle wrapper (one-time, if gradlew doesn't exist)
gradle wrapper

# Build debug APK
gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install on Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Target Device
- Samsung Galaxy S20
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)

## Package
- **App name:** Meal Tracker
- **Package:** com.fettbot.mealtracker
