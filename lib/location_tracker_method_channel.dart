import 'package:flutter/services.dart';
import 'package:location_tracker/location_tracker_platform_interface.dart';
import 'package:location_tracker/src/models/daily_distance.dart';
import 'package:location_tracker/src/models/exceptions.dart';
import 'package:location_tracker/src/models/location_point.dart';
import 'package:location_tracker/src/models/permission_status.dart';
import 'package:location_tracker/src/models/security_alert.dart';
import 'package:location_tracker/src/models/service_diagnostics.dart';
import 'package:location_tracker/src/models/tracking_status.dart';

class MethodChannelLocationTracker extends LocationTrackerPlatform {
  static const MethodChannel _channel =
      MethodChannel('com.harmonyloop.location_tracker');
  static const EventChannel _eventChannel =
      EventChannel('com.harmonyloop.location_tracker/events');

  Stream<LocationPoint>? _locationStream;
  Stream<TrackingStatus>? _statusStream;
  Stream<SecurityAlert>? _securityAlertStream;

  Future<T?> _invoke<T>(String method, [dynamic arguments]) async {
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } on PlatformException catch (e) {
      throw _mapPlatformException(e);
    }
  }

  LocationTrackerException _mapPlatformException(PlatformException e) {
    switch (e.code) {
      case 'PERMISSION_DENIED':
        return LocationPermissionDeniedException(
          message: e.message ?? 'Required permissions were denied',
          details: e.details,
        );
      case 'LOCATION_DISABLED':
      case 'LOCATION_SERVICES_DISABLED':
        return LocationServicesDisabledException(
          message: e.message ?? 'Location services are disabled',
          details: e.details,
        );
      case 'NOT_TRACKING':
      case 'TRACKING_NOT_ACTIVE':
        return LocationTrackingNotActiveException(
          message: e.message ?? 'Tracking is not currently active',
          details: e.details,
        );
      default:
        return LocationTrackerException(
          code: e.code,
          message: e.message ?? 'Platform operation failed: ${e.code}',
          details: e.details,
        );
    }
  }

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
  Stream<SecurityAlert> get onSecurityAlert {
    _securityAlertStream ??= _eventChannel
        .receiveBroadcastStream()
        .where((event) => event is Map && event['type'] == 'security_alert')
        .map((event) => SecurityAlert.fromMap(event as Map));
    return _securityAlertStream!;
  }

  @override
  Future<String?> getPlatformVersion() async {
    return await _invoke<String>('getPlatformVersion');
  }

  @override
  Future<LocationPermissionStatus> checkPermissions() async {
    final res = await _invoke<Map<dynamic, dynamic>>('checkPermissions');
    return LocationPermissionStatus.fromMap(res ?? {});
  }

  @override
  Future<LocationPermissionStatus> requestPermissions() async {
    final res = await _invoke<Map<dynamic, dynamic>>('requestPermissions');
    return LocationPermissionStatus.fromMap(res ?? {});
  }

  @override
  Future<void> startTracking([Map<String, dynamic>? config]) async {
    await _invoke<dynamic>('startTracking', config);
  }

  @override
  Future<void> stopTracking() async {
    await _invoke<dynamic>('stopTracking');
  }

  @override
  Future<bool> isTracking() async {
    final res = await _invoke<bool>('isTracking');
    return res ?? false;
  }

  @override
  Future<Map<String, dynamic>?> getLocationData() async {
    final data = await _invoke<Map<dynamic, dynamic>>('getLocationData');
    return data != null ? Map<String, dynamic>.from(data) : null;
  }

  @override
  Future<double> getTotalDistance() async {
    final distance = await _invoke<dynamic>('getTotalDistance');
    return distance != null ? (distance as num).toDouble() : 0.0;
  }

  @override
  Future<List<DailyDistance>> getDailyHistory({int days = 7}) async {
    final res = await _invoke<List<dynamic>>(
      'getDailyHistory',
      {'days': days},
    );
    if (res == null) return [];
    return res
        .whereType<Map<dynamic, dynamic>>()
        .map((e) => DailyDistance.fromMap(e))
        .toList();
  }

  @override
  Future<void> updateNotificationTitle(String title) async {
    await _invoke<dynamic>('updateNotificationTitle', {'title': title});
  }

  @override
  Future<bool> isIgnoringBatteryOptimizations() async {
    final res = await _invoke<bool>('isIgnoringBatteryOptimizations');
    return res ?? false;
  }

  @override
  Future<bool> requestIgnoreBatteryOptimizations() async {
    final res = await _invoke<bool>('requestIgnoreBatteryOptimizations');
    return res ?? false;
  }

  @override
  Future<bool> openOemBatterySettings() async {
    final res = await _invoke<bool>('openOemBatterySettings');
    return res ?? false;
  }

  @override
  Future<List<String>> getLogs() async {
    final res = await _invoke<List<dynamic>>('getLogs');
    return res?.map((e) => e.toString()).toList() ?? [];
  }

  @override
  Future<String?> exportLogsToFile() async {
    return await _invoke<String>('exportLogsToFile');
  }

  @override
  Future<bool> clearLogs() async {
    final res = await _invoke<bool>('clearLogs');
    return res ?? false;
  }

  @override
  Future<ServiceDiagnostics> getServiceDiagnostics() async {
    final res = await _invoke<Map<dynamic, dynamic>>('getServiceDiagnostics');
    return ServiceDiagnostics.fromMap(res ?? {});
  }
}
