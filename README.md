# 📍 Location Tracker

A simple Flutter plugin for real-time background location tracking and distance calculation on Android.

---

## 🚀 Features

- Real-time location updates
- Background tracking (Android foreground service)
- Total distance calculation
- Tap notification to open app
- Customizable notification title

---

## 🛠️ Quick Start

### 1. Add Dependency

**Local development:**
If you have the plugin source locally (for example, in a `plugins` folder next to your app):

```yaml
dependencies:
  location_tracker:
    path: ../plugins/location_tracker
```

**Or use from GitHub:**

```yaml
dependencies:
  location_tracker:
    git:
      url: https://github.com/shuhaibkt02/location_tracker.git
```

Run:
```sh
flutter pub get
```

---

### 2. Android Setup

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
```

---

### 3. Usage Example

```dart
import 'package:location_tracker/location_tracker.dart';

void startTracking() async {
  await LocationTracker.startTracking();
  await LocationTracker.updateNotificationTitle('Tracking...');
}

void stopTracking() async {
  await LocationTracker.stopTracking();
}

void getDistance() async {
  double distance = await LocationTracker.getTotalDistance();
  // Use distance as needed
}
```

- Tapping the notification opens your app.
- Change the notification title anytime with `updateNotificationTitle`.

---

## 📄 License

MIT

---

That’s it! For more, see the example app or open an issue if you need help.

