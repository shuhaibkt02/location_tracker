import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:location_tracker/location_tracker.dart';
import 'package:location_tracker/location_tracker_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockLocationTrackerPlatform
    with MockPlatformInterfaceMixin
    implements LocationTrackerPlatform {
  @override
  Stream<LocationPoint> get onLocationChanged => Stream.value(
        LocationPoint(
          latitude: 37.7749,
          longitude: -122.4194,
          accuracy: 5.0,
          speed: 1.2,
          altitude: 10.0,
          provider: 'gps',
          timestamp: DateTime.fromMillisecondsSinceEpoch(1700000000),
        ),
      );

  @override
  Stream<TrackingStatus> get onStatusChanged =>
      Stream.value(TrackingStatus.moving);

  @override
  Stream<SecurityAlert> get onSecurityAlert => Stream.value(
        const SecurityAlert(
          alertType: 'MOCK_LOCATION_DETECTED',
          timestamp: 1700000000,
          details: {'provider': 'mock'},
        ),
      );

  @override
  Future<LocationPermissionStatus> checkPermissions() async =>
      const LocationPermissionStatus(
        locationGranted: true,
        notificationGranted: true,
        isFineLocation: true,
        hasAllRequired: true,
      );

  @override
  Future<LocationPermissionStatus> requestPermissions() async =>
      const LocationPermissionStatus(
        locationGranted: true,
        notificationGranted: true,
        isFineLocation: true,
        hasAllRequired: true,
      );

  @override
  Future<void> startTracking([Map<String, dynamic>? config]) async {}

  @override
  Future<void> stopTracking() async {}

  @override
  Future<bool> isTracking() async => true;

  @override
  Future<double> getTodayDistance() async => 1520.5;

  @override
  Future<double> getTotalDistance() async => 1520.5;

  @override
  Future<List<DailyDistance>> getDailyHistory({int days = 7}) async => [
        DailyDistance(date: '2026-10-06', distanceMeters: 1520.5),
      ];

  @override
  Future<String?> getPlatformVersion() async => 'Android 14';

  @override
  Future<Map<String, dynamic>?> getLocationData() async => {
        'latitude': 37.7749,
        'longitude': -122.4194,
      };

  @override
  Future<void> updateNotificationTitle(String title) async {}

  @override
  Future<bool> isIgnoringBatteryOptimizations() async => true;

  @override
  Future<bool> requestIgnoreBatteryOptimizations() async => true;

  @override
  Future<bool> openOemBatterySettings() async => true;

  @override
  Future<List<String>> getLogs() async => ['[10:00:00.000] Service started'];

  @override
  Future<String?> exportLogsToFile() async => '/sdcard/logs.txt';

  @override
  Future<bool> clearLogs() async => true;

  @override
  Future<ServiceDiagnostics> getServiceDiagnostics() async =>
      const ServiceDiagnostics(
        isTracking: true,
        gpsEnabled: true,
        networkEnabled: true,
        isIgnoringBatteryOptimizations: true,
        isDeviceIdleMode: false,
        isPowerSaveMode: false,
        hasLastKnownLocation: true,
        lastLocationAgeMs: 500,
        lastLocationAccuracy: 4.2,
        lastLocationSpeed: 1.2,
        trackingStatus: 'MOVING',
        totalDistanceToday: 1520.5,
      );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Model Serialization & Parsing', () {
    test('TrackingConfig defaults and toMap/fromMap', () {
      const config = TrackingConfig(
        notificationTitle: 'Sales Tracker',
        speedThresholdMps: 0.8,
        enableAutoStop: true,
        autoStopHour: 18,
        autoStopMinute: 30,
        allowMockLocationsInDebug: false,
      );

      final map = config.toMap();
      expect(map['notificationTitle'], 'Sales Tracker');
      expect(map['speedThresholdMps'], 0.8);
      expect(map['enableAutoStop'], true);
      expect(map['autoStopHour'], 18);
      expect(map['autoStopMinute'], 30);
      expect(map['allowMockLocationsInDebug'], false);

      final restored = TrackingConfig.fromMap(map);
      expect(restored.notificationTitle, 'Sales Tracker');
      expect(restored.autoStopHour, 18);
      expect(restored.autoStopMinute, 30);
      expect(restored.allowMockLocationsInDebug, false);
    });

    test('LocationPoint fromMap & toMap', () {
      final point = LocationPoint.fromMap({
        'latitude': 12.9716,
        'longitude': 77.5946,
        'accuracy': 8.0,
        'speed': 1.5,
        'altitude': 920.0,
        'provider': 'gps',
        'timestamp': 1700000000,
      });

      expect(point.latitude, 12.9716);
      expect(point.longitude, 77.5946);
      expect(point.accuracy, 8.0);
      expect(point.speed, 1.5);
      expect(point.provider, 'gps');

      final map = point.toMap();
      expect(map['latitude'], 12.9716);
    });

    test('TrackingStatus string mapping', () {
      expect(TrackingStatus.fromString('MOVING'), TrackingStatus.moving);
      expect(TrackingStatus.fromString('STATIONARY'), TrackingStatus.stationary);
      expect(TrackingStatus.fromString('PAUSED'), TrackingStatus.paused);
      expect(TrackingStatus.fromString('STOPPED'), TrackingStatus.stopped);
      expect(TrackingStatus.fromString('RESUMED'), TrackingStatus.resumed);
      expect(TrackingStatus.fromString('UNKNOWN_VAL'), TrackingStatus.unknown);
    });

    test('SecurityAlert fromMap & toMap', () {
      final alert = SecurityAlert.fromMap({
        'alertType': 'MOCK_LOCATION_DETECTED',
        'timestamp': 1700000000,
        'details': {'provider': 'fake_gps'},
      });

      expect(alert.alertType, 'MOCK_LOCATION_DETECTED');
      expect(alert.timestamp, 1700000000);
      expect(alert.details['provider'], 'fake_gps');
      expect(alert.toMap()['alertType'], 'MOCK_LOCATION_DETECTED');
    });

    test('DailyDistance fromMap and km conversion', () {
      final daily = DailyDistance.fromMap({
        'date': '2026-10-06',
        'distance': 5432.1,
      });

      expect(daily.date, '2026-10-06');
      expect(daily.distanceMeters, 5432.1);
      expect(daily.distanceKm, closeTo(5.43, 0.01));
    });

    test('ServiceDiagnostics fromMap & toMap', () {
      final diag = ServiceDiagnostics.fromMap({
        'isTracking': true,
        'gpsEnabled': true,
        'networkEnabled': true,
        'isIgnoringBatteryOptimizations': true,
        'isDeviceIdleMode': false,
        'isPowerSaveMode': false,
        'hasLastKnownLocation': true,
        'lastLocationAgeMs': 350,
        'lastLocationAccuracy': 5.0,
        'lastLocationSpeed': 1.1,
        'trackingStatus': 'MOVING',
        'totalDistanceToday': 2500.0,
      });

      expect(diag.isTracking, true);
      expect(diag.gpsEnabled, true);
      expect(diag.totalDistanceToday, 2500.0);
      expect(diag.toMap()['trackingStatus'], 'MOVING');
    });
  });

  group('LocationTracker Platform Delegation', () {
    late MockLocationTrackerPlatform mockPlatform;

    setUp(() {
      mockPlatform = MockLocationTrackerPlatform();
      LocationTrackerPlatform.instance = mockPlatform;
    });

    test('checkPermissions delegates to platform', () async {
      final perms = await LocationTracker.checkPermissions();
      expect(perms.hasAllRequired, true);
    });

    test('getTodayDistance returns distance in meters', () async {
      final distance = await LocationTracker.getTodayDistance();
      expect(distance, 1520.5);
    });

    test('battery helpers delegate to platform', () async {
      expect(await LocationTracker.isIgnoringBatteryOptimizations(), true);
      expect(await LocationTracker.requestIgnoreBatteryOptimizations(), true);
      expect(await LocationTracker.openOemBatterySettings(), true);
    });

    test('diagnostics and logs delegate to platform', () async {
      final logs = await LocationTracker.getLogs();
      expect(logs, contains('[10:00:00.000] Service started'));

      final diag = await LocationTracker.getServiceDiagnostics();
      expect(diag.isTracking, true);
      expect(diag.totalDistanceToday, 1520.5);

      final exported = await LocationTracker.exportLogsToFile();
      expect(exported, '/sdcard/logs.txt');
    });

    test('streams emit expected events', () async {
      final point = await LocationTracker.onLocationChanged.first;
      expect(point.latitude, 37.7749);

      final status = await LocationTracker.onStatusChanged.first;
      expect(status, TrackingStatus.moving);

      final alert = await LocationTracker.onSecurityAlert.first;
      expect(alert.alertType, 'MOCK_LOCATION_DETECTED');
      expect(alert.isMockLocation, true);
    });
  });

  group('Typed Exceptions & Call-site Mapping', () {
    final methodChannel = MethodChannelLocationTracker();

    test('Maps PERMISSION_DENIED to LocationPermissionDeniedException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('com.harmonyloop.location_tracker'),
        (call) async {
          if (call.method == 'startTracking') {
            throw PlatformException(
              code: 'PERMISSION_DENIED',
              message: 'Permissions denied',
            );
          }
          return null;
        },
      );

      expect(
        () => methodChannel.startTracking(),
        throwsA(isA<LocationPermissionDeniedException>()),
      );
    });

    test('Maps LOCATION_DISABLED to LocationServicesDisabledException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('com.harmonyloop.location_tracker'),
        (call) async {
          if (call.method == 'startTracking') {
            throw PlatformException(
              code: 'LOCATION_DISABLED',
              message: 'GPS off',
            );
          }
          return null;
        },
      );

      expect(
        () => methodChannel.startTracking(),
        throwsA(isA<LocationServicesDisabledException>()),
      );
    });

    test('Maps NOT_TRACKING to LocationTrackingNotActiveException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('com.harmonyloop.location_tracker'),
        (call) async {
          if (call.method == 'getLocationData') {
            throw PlatformException(
              code: 'NOT_TRACKING',
              message: 'Session stopped',
            );
          }
          return null;
        },
      );

      expect(
        () => methodChannel.getLocationData(),
        throwsA(isA<LocationTrackingNotActiveException>()),
      );
    });

    test('Maps unknown error to base LocationTrackerException', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
        const MethodChannel('com.harmonyloop.location_tracker'),
        (call) async {
          if (call.method == 'stopTracking') {
            throw PlatformException(
              code: 'GENERIC_ERROR',
              message: 'Something broke',
            );
          }
          return null;
        },
      );

      expect(
        () => methodChannel.stopTracking(),
        throwsA(isA<LocationTrackerException>()),
      );
    });
  });
}

