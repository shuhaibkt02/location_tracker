import 'package:flutter/services.dart';
import 'package:location_tracker/location_tracker_platform_interface.dart';

class MethodChannelLocationTracker extends LocationTrackerPlatform {
  static const MethodChannel _channel = MethodChannel('location_tracker');

  @override
  Future<String?> getPlatformVersion() async {
    return _channel.invokeMethod<String>('getPlatformVersion');
  }

  @override
  Future<void> startTracking() async {
    try {
      await _channel.invokeMethod('startTracking');
    } on PlatformException catch (e) {
      throw 'Failed to start tracking: ${e.message}';
    }
  }

  @override
  Future<void> stopTracking() async {
    try {
      await _channel.invokeMethod('stopTracking');
    } on PlatformException catch (e) {
      throw 'Failed to stop tracking: ${e.message}';
    }
  }

  @override
  Future<Map<String, dynamic>?> getLocationData() async {
    try {
      final data = await _channel.invokeMethod('getLocationData');
      return data != null ? Map<String, dynamic>.from(data) : null;
    } on PlatformException catch (e) {
      throw 'Failed to get location data: ${e.message}';
    }
  }

  @override
  Future<double> getTotalDistance() async {
    try {
      final distance = await _channel.invokeMethod('getTotalDistance');
      return (distance as num).toDouble();
    } on PlatformException catch (e) {
      throw 'Failed to get total distance: ${e.message}';
    }
  }

  @override
  Future<void> updateNotificationTitle(String title) async {
    try {
      await _channel.invokeMethod('updateNotificationTitle', {'title': title});
    } on PlatformException catch (e) {
      throw 'Failed to update notification title: ${e.message}';
    }
  }
}
