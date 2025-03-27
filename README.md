### **📍 Location Tracker**  

A **Flutter plugin** for real-time location tracking with distance calculation. This plugin supports **Android-only** and provides **background location tracking** with easy-to-use APIs.  

---

## **🚀 Features**  

✅ Real-time location updates  
✅ Start/Stop background tracking  
✅ Retrieve last known location  
✅ Calculate total distance traveled  
✅ Runs as a **foreground service (Android 10+)**  
✅ Optimized for **Android 14 compliance**  

---

## **📦 Step-by-Step Installation Guide**  

### **Step 1: Add Dependency**  

Add the `location_tracker` plugin to your project by modifying `pubspec.yaml`:  

```yaml
dependencies:
  flutter:
    sdk: flutter
  location_tracker:
    path: ../  # Update this path based on your project structure
```
OR

```yaml
dependencies:
  flutter:
    sdk: flutter
  location_tracker:
    git:
      url: https://github.com/shuhaibkt02/location_tracker.git
```


Then, run the following command to fetch dependencies:  

```sh
flutter pub get
```  

---

## **🔑 Android Setup**  

### **Step 2: Update AndroidManifest.xml**  

Modify `android/app/src/main/AndroidManifest.xml` and add these permissions inside the `<manifest>` tag:  

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
```  

Inside the `<application>` tag, enable **background location tracking** with a foreground service:  

```xml
<service
    android:name=".LocationService"
    android:enabled="true"
    android:foregroundServiceType="location"
    android:exported="false"/>
```  

For **Android 14 compliance**, a **persistent notification** must be displayed when tracking location in the background.  

---

## **🛠️ Kotlin and Gradle Configuration**  

### **Step 3: Update Kotlin Version**  

Ensure your `android/gradle.properties` contains:  

```properties
kotlin.gradle.plugin.version=1.9.22
```  

### **Step 4: Update compileSdk & targetSdk**  

Modify `android/app/build.gradle`:  

```gradle
android {
    compileSdkVersion 34

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
    }
}
```  

---

## **🔧 Battery Optimization (Android 12+)**  

If tracking **stops unexpectedly**, Android may be killing your background service.  

To **ignore battery optimizations**, ask users to **manually allow location tracking** in settings.  

```dart
import 'package:android_intent_plus/android_intent.dart';
import 'package:android_intent_plus/flag.dart';

Future<void> openBatteryOptimizationSettings() async {
  final intent = AndroidIntent(
    action: 'android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    package: 'your.package.name',
  );
  await intent.launch();
}
```  

---

## **🛠️ Initialize & Use the Plugin**  

### **Step 5: Initialize the Plugin**  

Create an instance of `LocationTracker` in your Flutter app:  

```dart
final _locationTracker = LocationTracker.instance;
```  

### **Step 6: Start Location Tracking**  

```dart
await _locationTracker.startTracking();
```  

### **Step 7: Stop Location Tracking**  

```dart
await _locationTracker.stopTracking();
```  

### **Step 8: Get Current Location Data**  

```dart
Map<String, dynamic>? location = await _locationTracker.getLocationData();
print("Current Location: $location");
```  

### **Step 9: Get Total Distance Traveled**  

```dart
double? distance = await _locationTracker.getTotalDistance();
print("Total Distance: $distance meters");
```  

---

## **📌 Complete Example Code**  

```dart
import 'package:flutter/material.dart';
import 'package:location_tracker/location_tracker.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  MyAppState createState() => MyAppState();
}

class MyAppState extends State<MyApp> {
  double _totalDistance = 0.0;
  final List<Map<String, dynamic>> _locationList = [];

  Future<void> _startTracking() async {
    await LocationTracker.startTracking();
  }

  Future<void> _stopTracking() async {
    await LocationTracker.stopTracking();
  }

  Future<void> _getTotalDistance() async {
    final distance = await LocationTracker.getTotalDistance();
    setState(() {
      _totalDistance = distance;
    });
  }

  Future<void> _getLocationData() async {
    final location = await LocationTracker.getLocationData();

    print(location);

    if (location != null) {
      setState(() {
        _locationList.add(location);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: Text('Location Tracker Test')),
        body: Column(
          children: [
            ElevatedButton(onPressed: _startTracking, child: Text("Start")),
            ElevatedButton(onPressed: _stopTracking, child: Text("Stop")),
            Text("Total Distance: $_totalDistance meters"),
            ElevatedButton(onPressed: _getTotalDistance, child: Text("Get Distance")),
            ElevatedButton(onPressed: _getLocationData, child: Text("Get Location")),
          ],
        ),
      ),
    );
  }
}
```  

---

## **💼 License**  

This project is licensed under the **MIT License**.  

Let me know if you need **further refinements!** 🚀

