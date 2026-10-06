import 'package:flutter/services.dart';
import 'package:location_tracker/location_tracker_platform_interface.dart';
import 'package:location_tracker/src/models/daily_distance.dart';
import 'package:location_tracker/src/models/exceptions.dart';
import 'package:location_tracker/src/models/location_point.dart';
import 'package:location_tracker/src/models/permission_status.dart';
import 'package:location_tracker/src/models/tracking_status.dart';

class MethodChannelLocationTracker extends LocationTrackerPlatform {
  static const MethodChannel _channel = MethodChannel('location_tracker');
  static const EventChannel _eventChannel = EventChannel('location_tracker/events');

  Stream<LocationPoint>? _locationStream;
  Stream<TrackingStatus>? _statusStream;

  @override
  Stream<LocationPoint> get onLocationChanged {
    _locationStream ??= _eventChannel
        .receiveBroadcastStream()
        .where((event) => event is Map && event['type'] == 'location')
        .map((event) => LocationPoint.fromMap(event as Map));
    return _locationStream!;
  }

  @override
  Stream<TrackingStatus> get onStatusChanged {
    _statusStream ??= _eventChannel
        .receiveBroadcastStream()
        .where((event) => event is Map && event['type'] == 'status')
        .map((event) =>
            TrackingStatus.fromString((event as Map)['status'] as String?));
    return _statusStream!;
  }

  @override
  Future<String?> getPlatformVersion() async {
    try {
      return await _channel.invokeMethod<String>('getPlatformVersion');
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to get platform version',
        details: e.details,
      );
    }
  }

  @override
  Future<LocationPermissionStatus> checkPermissions() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('checkPermissions');
      return LocationPermissionStatus.fromMap(res ?? {});
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to check permissions',
        details: e.details,
      );
    }
  }

  @override
  Future<LocationPermissionStatus> requestPermissions() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('requestPermissions');
      return LocationPermissionStatus.fromMap(res ?? {});
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to request permissions',
        details: e.details,
      );
    }
  }

  @override
  Future<void> startTracking([Map<String, dynamic>? config]) async {
    try {
      await _channel.invokeMethod('startTracking', config);
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to start tracking',
        details: e.details,
      );
    }
  }

  @override
  Future<void> stopTracking() async {
    try {
      await _channel.invokeMethod('stopTracking');
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to stop tracking',
        details: e.details,
      );
    }
  }

  @override
  Future<bool> isTracking() async {
    try {
      final res = await _channel.invokeMethod<bool>('isTracking');
      return res ?? false;
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to check tracking state',
        details: e.details,
      );
    }
  }

  @override
  Future<Map<String, dynamic>?> getLocationData() async {
    try {
      final data = await _channel.invokeMethod('getLocationData');
      return data != null ? Map<String, dynamic>.from(data) : null;
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to get location data',
        details: e.details,
      );
    }
  }

  @override
  Future<double> getTotalDistance() async {
    try {
      final distance = await _channel.invokeMethod('getTotalDistance');
      return distance != null ? (distance as num).toDouble() : 0.0;
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to get total distance',
        details: e.details,
      );
    }
  }

  @override
  Future<List<DailyDistance>> getDailyHistory({int days = 7}) async {
    try {
      final res = await _channel.invokeMethod<List<dynamic>>(
        'getDailyHistory',
        {'days': days},
      );
      if (res == null) return [];
      return res
          .whereType<Map<dynamic, dynamic>>()
          .map((e) => DailyDistance.fromMap(e))
          .toList();
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to get daily history',
        details: e.details,
      );
    }
  }

  @override
  Future<void> updateNotificationTitle(String title) async {
    try {
      await _channel.invokeMethod('updateNotificationTitle', {'title': title});
    } on PlatformException catch (e) {
      throw LocationTrackerException(
        code: e.code,
        message: e.message ?? 'Failed to update notification title',
        details: e.details,
      );
    }
  }
}
