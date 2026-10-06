library;

export 'location_tracker_platform_interface.dart' show LocationTrackerPlatform;
export 'src/models/daily_distance.dart';
export 'src/models/exceptions.dart';
export 'src/models/location_point.dart';
export 'src/models/permission_status.dart';
export 'src/models/security_alert.dart';
export 'src/models/tracking_config.dart';
export 'src/models/tracking_status.dart';

import 'package:location_tracker/location_tracker_platform_interface.dart';
import 'package:location_tracker/src/models/daily_distance.dart';
import 'package:location_tracker/src/models/location_point.dart';
import 'package:location_tracker/src/models/permission_status.dart';
import 'package:location_tracker/src/models/security_alert.dart';
import 'package:location_tracker/src/models/tracking_config.dart';
import 'package:location_tracker/src/models/tracking_status.dart';

class LocationTracker {
  LocationTracker._();

  static LocationTrackerPlatform get _platform =>
      LocationTrackerPlatform.instance;

  /// Emits real-time location fixes during active tracking.
  static Stream<LocationPoint> get onLocationChanged =>
      _platform.onLocationChanged;

  /// Emits real-time movement and tracking state transitions (STATIONARY, MOVING, PAUSED).
  static Stream<TrackingStatus> get onStatusChanged =>
      _platform.onStatusChanged;

  /// Emits security alerts when tamper or fake GPS mock locations are detected.
  static Stream<SecurityAlert> get onSecurityAlert =>
      _platform.onSecurityAlert;

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

  /// Retrieves the accumulated distance traveled today in meters.
  static Future<double> getTodayDistance() async {
    return _platform.getTodayDistance();
  }

  /// Retrieves the past [days] of recorded daily distances (default: 7 days).
  static Future<List<DailyDistance>> getDailyHistory({int days = 7}) async {
    return _platform.getDailyHistory(days: days);
  }

  /// Updates the foreground notification title dynamically.
  static Future<void> updateNotificationTitle(String title) async {
    return _platform.updateNotificationTitle(title);
  }
}
