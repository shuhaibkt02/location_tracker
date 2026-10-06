# 📍 Location Tracker

**Enterprise background location and mileage tracking for Flutter (Android).**  
Built for workforce apps, delivery fleets, and field sales teams where travel distance directly impacts payroll and expense reimbursements.

---

### 💡 Why Use This Plugin? (Real Impact & Outcome)

Standard location plugins stop tracking when the app is closed, waste battery, or count fake distance while sitting still. **Location Tracker solves those problems:**

* 🛡️ **Never Loses a Shift:** Keeps tracking even if Android kills the app under memory pressure, the user swipes it away, or the phone restarts.
* 🎯 **Accurate Mileage (No GPS Drift):** Uses an intelligent Kalman filter and speed thresholds to discard GPS jitter when walking indoors or sitting at a desk.
* 🚫 **Fraud & Tamper Protection:** Detects and discards fake GPS spoofing apps (mock locations) and alerts your app if GPS or permissions are turned off mid-shift.
* 💾 **Offline Daily Totals:** Automatically saves mileage day-by-day (with clean midnight shift rollovers) into a local database.
* 🔋 **Battery & OEM Friendly:** Handles Android Doze mode and provides 1-tap shortcuts for aggressive battery savers (Samsung, Xiaomi, Oppo).

---

## 🚀 Quick Start in 3 Steps

### 1. Add Dependency

In your `pubspec.yaml`:

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

In `android/app/src/main/AndroidManifest.xml`, add these permissions inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

---

### 3. Start Tracking (Copy & Paste)

```dart
import 'package:flutter/material.dart';
import 'package:location_tracker/location_tracker.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 1. Request permissions
  final status = await LocationTracker.requestPermissions();
  if (!status.isGranted) return;

  // 2. Start tracking with custom notification and settings
  await LocationTracker.startTracking(
    const TrackingConfig(
      notificationTitle: "Shift Tracking Active",
      notificationBodyTemplate: "Traveled: {distance} km • {status}",
      updateIntervalMs: 8000, // Update every 8 seconds
      autoResumeOnBoot: true,  // Auto-resume if phone reboots
    ),
  );

  // 3. Listen to live status (MOVING, STATIONARY, PAUSED)
  LocationTracker.onStatusChanged.listen((status) {
    print("User is currently: ${status.name}");
  });

  // 4. Catch security alerts (Fake GPS or GPS turned off)
  LocationTracker.onSecurityAlert.listen((alert) {
    if (alert.isMockLocation) {
      print("⚠️ Tamper Alert: User is using a Fake GPS app!");
    } else if (alert.isLocationDisabled) {
      print("⚠️ Warning: GPS was turned off!");
    }
  });
}
```

---

## 📊 Common Actions

### Get Today's Traveled Distance
```dart
double meters = await LocationTracker.getTodayDistance();
double km = meters / 1000.0;
print("Traveled today: ${km.toStringAsFixed(2)} km");
```

### View Past 7 Days History
```dart
List<DailyDistance> history = await LocationTracker.getDailyHistory(days: 7);
for (var record in history) {
  print("${record.date}: ${record.distanceKm.toStringAsFixed(2)} km");
}
```

### Stop Tracking
```dart
await LocationTracker.stopTracking();
```

### Fix Battery Killers (Samsung, Xiaomi, Oppo)
```dart
// Request system battery optimization exemption
if (!await LocationTracker.isIgnoringBatteryOptimizations()) {
  await LocationTracker.requestIgnoreBatteryOptimizations();
}

// Open phone manufacturer's auto-start / background settings page
await LocationTracker.openOemBatterySettings();
```

---

## 🛡️ Clean Error Handling

No fragile error string parsing needed. Catch typed exceptions directly:

```dart
try {
  await LocationTracker.startTracking();
} on LocationPermissionDeniedException {
  // Show "Please enable location permission" dialog
} on LocationServicesDisabledException {
  // Prompt user to turn on GPS in settings
} catch (e) {
  // Handle other unexpected errors
}
```

---

## 📄 License

MIT License.
