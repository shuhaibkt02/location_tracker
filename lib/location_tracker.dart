library;

export 'location_tracker_platform_interface.dart' show LocationTrackerPlatform;

import 'package:location_tracker/location_tracker_platform_interface.dart';

class LocationTracker {
  LocationTracker._();

  static LocationTrackerPlatform get _platform =>
      LocationTrackerPlatform.instance;

  static Future<String?> get platformVersion async {
    return _platform.getPlatformVersion();
  }

  static Future<void> startTracking() async {
    return _platform.startTracking();
  }

  static Future<void> stopTracking() async {
    return _platform.stopTracking();
  }

  static Future<Map<String, dynamic>?> getLocationData() async {
    return _platform.getLocationData();
  }

  static Future<double> getTotalDistance() async {
    return _platform.getTotalDistance();
  }

  static Future<void> updateNotificationTitle(String title) async {
    return _platform.updateNotificationTitle(title);
  }
}
