/// Base exception for location tracker plugin operations.
class LocationTrackerException implements Exception {
  final String code;
  final String message;
  final dynamic details;

  const LocationTrackerException({
    required this.code,
    required this.message,
    this.details,
  });

  @override
  String toString() => 'LocationTrackerException($code): $message';
}

/// Thrown when required location or notification permissions are missing or denied.
class LocationPermissionDeniedException extends LocationTrackerException {
  const LocationPermissionDeniedException({
    super.message = 'Required location and notification permissions must be granted.',
    super.details,
  }) : super(
          code: 'PERMISSION_DENIED',
        );

  @override
  String toString() => 'LocationPermissionDeniedException: $message';
}

/// Thrown when system GPS or network location services are disabled.
class LocationServicesDisabledException extends LocationTrackerException {
  const LocationServicesDisabledException({
    super.message = 'System location services (GPS/Network) are disabled.',
    super.details,
  }) : super(
          code: 'LOCATION_DISABLED',
        );

  @override
  String toString() => 'LocationServicesDisabledException: $message';
}

/// Thrown when querying active tracking data while tracking is not active.
class LocationTrackingNotActiveException extends LocationTrackerException {
  const LocationTrackingNotActiveException({
    super.message = 'Tracking is not currently active.',
    super.details,
  }) : super(
          code: 'NOT_TRACKING',
        );

  @override
  String toString() => 'LocationTrackingNotActiveException: $message';
}
