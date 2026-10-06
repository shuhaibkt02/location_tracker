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
