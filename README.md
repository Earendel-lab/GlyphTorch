<img width="500" height="500" alt="NothingSpace_Mockup_1782089812982" src="https://github.com/user-attachments/assets/e4b84214-f6b2-45e9-9393-d1f0af5cf1c3" /> <img width="500" height="500" alt="NothingSpace_Mockup_1782089823724" src="https://github.com/user-attachments/assets/5052e0da-4e3c-48d7-aa47-7c4177dee4bf" />

# Glyph Torch

A minimal, single toggle utility for Nothing devices. Controls all Glyph LEDs simultaneously with one master toggle.

Built with the [Nothing Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit).

## Features

- Single master glyph toggle controls all glyph zones
- Pure monochrome design — AMOLED black / white with red accent
- Edge-to-edge, Nothing-inspired minimal UI
- Automatic device detection (Phone 1, 2, 2a, 2a Plus, 3a, 3a pro...)

## Requirements

- Nothing device on Android 14+ (SDK limitation)
- Foreground usage only (SDK restriction)
- Android Studio with AGP 8.11+
- JDK 17

## SDK Integration

This project uses the Nothing Glyph Developer Kit:

- Repo: https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit
- Download `KetchumSDK_Community_YYYYMMDD.jar` from the `sdk/` folder and place it in `app/libs/`
- The build already includes `implementation(fileTree(dir: "libs", include: ["*.jar", "*.aar"]))`

### Manifest

```xml
<uses-permission android:name="com.nothing.ketchum.permission.ENABLE" />

<application>
    <!-- Use "test" for development, replace with your real API key for production -->
    <meta-data android:name="NothingKey" android:value="test" />
</application>
```

## Build and Run

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug
```

Enable Glyph SDK debug mode (recommended for development):

```bash
adb shell settings put global nt_glyph_interface_debug_enable 1
```


## Notes

- SDK only works on Nothing devices on Android 14+
- Debug mode auto-expires after ~48h
- Only foreground apps may control glyphs

## Author

Earendel

## Credits

- Nothing Glyph Developer Kit — © Nothing, used under the terms in their repository
# Glyph-Torch
