/// System diagnostics and health state for the location tracking service.
class ServiceDiagnostics {
  final bool isTracking;
  final bool gpsEnabled;
  final bool networkEnabled;
  final bool isIgnoringBatteryOptimizations;
  final bool isDeviceIdleMode;
  final bool isPowerSaveMode;
  final bool hasLastKnownLocation;
  final int lastLocationAgeMs;
  final double lastLocationAccuracy;
  final double lastLocationSpeed;
  final String trackingStatus;
  final double totalDistanceToday;

  const ServiceDiagnostics({
    required this.isTracking,
    required this.gpsEnabled,
    required this.networkEnabled,
    required this.isIgnoringBatteryOptimizations,
    required this.isDeviceIdleMode,
    required this.isPowerSaveMode,
    required this.hasLastKnownLocation,
    required this.lastLocationAgeMs,
    required this.lastLocationAccuracy,
    required this.lastLocationSpeed,
    required this.trackingStatus,
    required this.totalDistanceToday,
  });

  factory ServiceDiagnostics.fromMap(Map<dynamic, dynamic> map) {
    return ServiceDiagnostics(
      isTracking: map['isTracking'] as bool? ?? false,
      gpsEnabled: map['gpsEnabled'] as bool? ?? false,
      networkEnabled: map['networkEnabled'] as bool? ?? false,
      isIgnoringBatteryOptimizations:
          map['isIgnoringBatteryOptimizations'] as bool? ?? false,
      isDeviceIdleMode: map['isDeviceIdleMode'] as bool? ?? false,
      isPowerSaveMode: map['isPowerSaveMode'] as bool? ?? false,
      hasLastKnownLocation: map['hasLastKnownLocation'] as bool? ?? false,
      lastLocationAgeMs: (map['lastLocationAgeMs'] as num?)?.toInt() ?? -1,
      lastLocationAccuracy:
          (map['lastLocationAccuracy'] as num?)?.toDouble() ?? -1.0,
      lastLocationSpeed:
          (map['lastLocationSpeed'] as num?)?.toDouble() ?? -1.0,
      trackingStatus: map['trackingStatus'] as String? ?? 'UNKNOWN',
      totalDistanceToday:
          (map['totalDistanceToday'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'isTracking': isTracking,
      'gpsEnabled': gpsEnabled,
      'networkEnabled': networkEnabled,
      'isIgnoringBatteryOptimizations': isIgnoringBatteryOptimizations,
      'isDeviceIdleMode': isDeviceIdleMode,
      'isPowerSaveMode': isPowerSaveMode,
      'hasLastKnownLocation': hasLastKnownLocation,
      'lastLocationAgeMs': lastLocationAgeMs,
      'lastLocationAccuracy': lastLocationAccuracy,
      'lastLocationSpeed': lastLocationSpeed,
      'trackingStatus': trackingStatus,
      'totalDistanceToday': totalDistanceToday,
    };
  }

  @override
  String toString() =>
      'ServiceDiagnostics(isTracking: $isTracking, status: $trackingStatus, gpsEnabled: $gpsEnabled, distance: $totalDistanceToday m)';
}
