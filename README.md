# MomentTrack Scanner

A native Android app for continuous QR code and barcode monitoring in manufacturing environments. Designed as an "eye in the sky" that passively scans and logs all codes visible to the camera.

## Features

- **Continuous Scanning**: Uses CameraX + ML Kit for real-time barcode detection
- **Smart Debouncing**: Configurable per-location debounce intervals to prevent duplicate logs
- **Location Auto-Detection**: Scans station QR codes (LOC:, STATION:, MT-LOC: prefixes) to self-identify location
- **Offline-First**: Local Room database stores scans, syncs when connected
- **Auto-Start on Boot**: Optional setting to launch automatically when device powers on
- **Foreground Service**: Keeps scanning even when minimized
- **GPS Tagging**: Attaches device coordinates to each scan

## Supported Barcode Formats

- QR Code
- Data Matrix
- Code 128, Code 39
- EAN-13, EAN-8
- UPC-A, UPC-E
- PDF417
- Aztec

## Building the APK

### Option 1: GitHub Actions (Recommended)

1. Push this code to a GitHub repository
2. Go to **Actions** tab
3. The workflow runs automatically on push, or click **Run workflow** manually
4. Download the APK from **Artifacts** when complete

### Option 2: Android Studio

1. Open this project in Android Studio
2. Wait for Gradle sync to complete
3. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### Option 3: Command Line

```bash
# On Mac/Linux
./gradlew assembleDebug

# On Windows
gradlew.bat assembleDebug
```

## Installation

1. Enable **Install from Unknown Sources** in Android settings
2. Transfer the APK to your device
3. Tap the APK file to install
4. Grant permissions when prompted:
   - Camera (required)
   - Location (optional, for GPS tagging)
   - Notifications (for foreground service)

## Configuration

On first launch, go to **Settings** (gear icon) to configure:

| Setting | Description | Default |
|---------|-------------|---------|
| API Endpoint | MomentTrack server URL | https://api.momenttrack.com/v1 |
| Device ID | Unique identifier for this scanner | Auto-generated (MT-XXXXXXXX) |
| Debounce | Seconds before re-logging same code | 30 |
| Sound | Beep on scan | On |
| Vibrate | Haptic feedback on scan | On |
| Auto-start | Launch on device boot | Off |

## API Integration

The app POSTs scans to your configured endpoint:

```json
POST /scans
{
  "id": "MT-ABC123-1705123456789",
  "code": "PART-001234",
  "format": "QR_CODE",
  "timestamp": "2025-01-13T10:30:45.123Z",
  "deviceId": "MT-ABC123",
  "locationId": "STATION-A1",
  "gps": {
    "lat": 40.2968,
    "lng": -111.6946
  }
}
```

### Location Config Endpoint

When a station code is scanned, the app fetches location-specific config:

```json
GET /locations/{locationId}/config

Response:
{
  "id": "STATION-A1",
  "displayName": "Assembly Station 1",
  "debounceSeconds": 60
}
```

## Project Structure

```
MomentTrackScanner/
├── app/
│   ├── src/main/
│   │   ├── java/com/momenttrack/scanner/
│   │   │   ├── MainActivity.kt        # Main scanner UI
│   │   │   ├── SettingsActivity.kt    # Settings screen
│   │   │   ├── MomentTrackApp.kt      # Application class
│   │   │   ├── data/
│   │   │   │   ├── ScanRecord.kt      # Room database + DAO
│   │   │   │   └── SettingsRepository.kt  # DataStore prefs
│   │   │   ├── network/
│   │   │   │   └── ApiClient.kt       # Retrofit API
│   │   │   ├── service/
│   │   │   │   └── ScannerService.kt  # Foreground service
│   │   │   └── receiver/
│   │   │       └── BootReceiver.kt    # Auto-start on boot
│   │   ├── res/
│   │   │   ├── layout/                # XML layouts
│   │   │   ├── drawable/              # Icons & shapes
│   │   │   └── values/                # Colors, strings, themes
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/
│   └── build.yml                      # GitHub Actions CI
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Requirements

- Android 7.0+ (API 24)
- Camera with autofocus recommended
- Internet connection for sync (works offline with local storage)

## License

Proprietary - MomentTrack LLC
