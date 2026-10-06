/// Status of required runtime permissions for workforce location tracking.
class LocationPermissionStatus {
  /// Whether either fine or coarse location permission is granted.
  final bool locationGranted;

  /// Whether notification permission is granted (always true on Android < 13).
  final bool notificationGranted;

  /// Whether high-accuracy fine location is granted.
  final bool isFineLocation;

  /// Whether all permissions required for foreground tracking are satisfied.
  final bool hasAllRequired;

  const LocationPermissionStatus({
    required this.locationGranted,
    required this.notificationGranted,
    required this.isFineLocation,
    required this.hasAllRequired,
  });

  factory LocationPermissionStatus.fromMap(Map<dynamic, dynamic> map) {
    return LocationPermissionStatus(
      locationGranted: map['locationGranted'] as bool? ?? false,
      notificationGranted: map['notificationGranted'] as bool? ?? false,
      isFineLocation: map['isFineLocation'] as bool? ?? false,
      hasAllRequired: map['hasAllRequired'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'locationGranted': locationGranted,
      'notificationGranted': notificationGranted,
      'isFineLocation': isFineLocation,
      'hasAllRequired': hasAllRequired,
    };
  }

  @override
  String toString() =>
      'LocationPermissionStatus(hasAllRequired: $hasAllRequired, isFineLocation: $isFineLocation, notificationGranted: $notificationGranted)';
}
