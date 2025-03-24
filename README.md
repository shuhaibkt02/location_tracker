# 📍 Location Tracker

A **Flutter plugin** for real-time location tracking with distance calculation. This plugin supports **Android-only** and provides **background location tracking** with easy-to-use APIs.

---

# 🚀 Features

✅ Real-time location updates  
✅ Start/Stop background tracking  
✅ Retrieve last known location  
✅ Calculate total distance traveled  
✅ Lightweight and efficient  

---

# 📦 Step-by-Step Installation Guide

### **Step 1: Add Dependency**
Add the `location_tracker` plugin to your project by modifying `pubspec.yaml`:

```yaml
dependencies:
  flutter:
    sdk: flutter
  location_tracker:
    path: ../  # Update this path based on your project structure
```

Then, run the following command to fetch dependencies:

```sh
flutter pub get
```

---

# 🛠️ Step-by-Step Implementation Guide

### **Step 2: Import the Plugin**
Open your **Dart file** (e.g., `main.dart`) and import the plugin:

```dart
import 'package:location_tracker/location_tracker.dart';
```

---

### **Step 3: Request Permissions**
For the plugin to work properly, **location permissions** must be enabled.

#### **Android Permissions**
Modify `android/app/src/main/AndroidManifest.xml` and add these lines inside the `<manifest>` tag:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
```

Then, inside `<application>` tag, enable **background location tracking**:

```xml
<service
    android:name=".LocationService"
    android:enabled="true"
    android:foregroundServiceType="location" />
```

---

### **Step 4: Initialize the Plugin**
Create an instance of `LocationTracker` in your Flutter app:

```dart
final _locationTracker = LocationTracker.instance;
```

---

### **Step 5: Start Location Tracking**
Call `startTracking()` to begin tracking in the background:

```dart
await _locationTracker.startTracking();
```

---

### **Step 6: Stop Location Tracking**
If you want to stop tracking, call:

```dart
await _locationTracker.stopTracking();
```

---

### **Step 7: Get Current Location Data**
Retrieve the current **latitude** and **longitude**:

```dart
Map<String, dynamic>? location = await _locationTracker.getLocationData();
print("Current Location: $location");
```

---

### **Step 8: Get Total Distance Traveled**
To check how far the user has moved:

```dart
double? distance = await _locationTracker.getTotalDistance();
print("Total Distance: $distance meters");
```

---

# 📌 Complete Example Code
Here’s a **fully working Flutter example** that integrates the plugin:

```dart
import 'package:flutter/material.dart';
import 'package:location_tracker/location_tracker.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _locationTracker = LocationTracker.instance;
  String _location = 'Unknown';
  double _distance = 0.0;

  @override
  void initState() {
    super.initState();
    
  }


  Future<void> _startTracking() async {
    await _locationTracker.startTracking();
    setState(() {});
  }

  Future<void> _stopTracking() async {
    await _locationTracker.stopTracking();
    setState(() {});
  }

  Future<void> _getLocation() async {
    Map<String, dynamic>? loc = await _locationTracker.getLocationData();
    setState(() => _location = loc.toString());
  }

  Future<void> _getDistance() async {
    double? dist = await _locationTracker.getTotalDistance();
    setState(() => _distance = dist ?? 0.0);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('Location Tracker Example')),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Current Location: $_location'),
            Text('Total Distance: $_distance meters'),
            ElevatedButton(
              onPressed: _startTracking,
              child: const Text('Start Tracking'),
            ),
            ElevatedButton(
              onPressed: _stopTracking,
              child: const Text('Stop Tracking'),
            ),
            ElevatedButton(
              onPressed: _getLocation,
              child: const Text('Get Location'),
            ),
            ElevatedButton(
              onPressed: _getDistance,
              child: const Text('Get Distance'),
            ),
          ],
        ),
      ),
    );
  }
}
```

---

# 📌 Notes
- This plugin **only supports Android**.
- Ensure you have **enabled location permissions** in your app settings.
- The app must be **running in the foreground or background** for tracking to work.

---

# 🎯 Contributing
Feel free to **open an issue** or **submit a pull request** if you find a bug or want to improve the plugin.

---

# 📄 License
This project is licensed under the **MIT License**.

