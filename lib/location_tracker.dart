library;

export 'location_tracker_platform_interface.dart' show LocationTrackerPlatform;
export 'src/models/exceptions.dart';
export 'src/models/permission_status.dart';
export 'src/models/tracking_config.dart';

import 'package:location_tracker/location_tracker_platform_interface.dart';
import 'package:location_tracker/src/models/permission_status.dart';
import 'package:location_tracker/src/models/tracking_config.dart';

class LocationTracker {
  LocationTracker._();

  static LocationTrackerPlatform get _platform =>
      LocationTrackerPlatform.instance;

  static Future<String?> get platformVersion async {
    return _platform.getPlatformVersion();
  }

  /// Checks current location and notification permission status.
  static Future<LocationPermissionStatus> checkPermissions() async {
    return _platform.checkPermissions();
  }

  /// Requests location and notification permissions from the user.
  static Future<LocationPermissionStatus> requestPermissions() async {
    return _platform.requestPermissions();
  }

  /// Starts foreground location tracking with optional configuration.
  static Future<void> startTracking([
    TrackingConfig config = const TrackingConfig(),
  ]) async {
    return _platform.startTracking(config.toMap());
  }

  /// Stops location tracking and dismisses the foreground notification.
  static Future<void> stopTracking() async {
    return _platform.stopTracking();
  }

  /// Checks if tracking is currently active.
  static Future<bool> isTracking() async {
    return _platform.isTracking();
  }

  /// Retrieves the last recorded location point.
  static Future<Map<String, dynamic>?> getLocationData() async {
    return _platform.getLocationData();
  }

  /// Retrieves the accumulated distance traveled today in meters.
  static Future<double> getTotalDistance() async {
    return _platform.getTotalDistance();
  }

  /// Updates the foreground notification title dynamically.
  static Future<void> updateNotificationTitle(String title) async {
    return _platform.updateNotificationTitle(title);
  }
}
