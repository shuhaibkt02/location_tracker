import 'package:location_tracker/location_tracker_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

abstract class LocationTrackerPlatform extends PlatformInterface {
  LocationTrackerPlatform() : super(token: _token);

  static final Object _token = Object();
  static LocationTrackerPlatform _instance = MethodChannelLocationTracker();

  static LocationTrackerPlatform get instance => _instance;

  static set instance(LocationTrackerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion();
  Future<void> startTracking();
  Future<void> stopTracking();
  Future<Map<String, dynamic>?> getLocationData();
  Future<double> getTotalDistance();
  Future<void> updateNotificationTitle(String title);
}
