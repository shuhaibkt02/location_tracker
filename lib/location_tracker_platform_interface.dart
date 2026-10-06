import 'package:location_tracker/location_tracker_method_channel.dart';
import 'package:location_tracker/src/models/location_point.dart';
import 'package:location_tracker/src/models/permission_status.dart';
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

  Stream<LocationPoint> get onLocationChanged {
    throw UnimplementedError('onLocationChanged has not been implemented.');
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('getPlatformVersion() has not been implemented.');
  }

  Future<LocationPermissionStatus> checkPermissions() {
    throw UnimplementedError('checkPermissions() has not been implemented.');
  }

  Future<LocationPermissionStatus> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  Future<void> startTracking([Map<String, dynamic>? config]) {
    throw UnimplementedError('startTracking() has not been implemented.');
  }

  Future<void> stopTracking() {
    throw UnimplementedError('stopTracking() has not been implemented.');
  }

  Future<bool> isTracking() {
    throw UnimplementedError('isTracking() has not been implemented.');
  }

  Future<Map<String, dynamic>?> getLocationData() {
    throw UnimplementedError('getLocationData() has not been implemented.');
  }

  Future<double> getTotalDistance() {
    throw UnimplementedError('getTotalDistance() has not been implemented.');
  }

  Future<void> updateNotificationTitle(String title) {
    throw UnimplementedError('updateNotificationTitle() has not been implemented.');
  }
}
