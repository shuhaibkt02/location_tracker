/// Represents a security violation or tamper attempt detected during tracking.
class SecurityAlert {
  static const String mockLocationDetected = 'MOCK_LOCATION_DETECTED';
  static const String locationDisabled = 'LOCATION_DISABLED';
  static const String permissionLost = 'PERMISSION_LOST';

  /// The type of alert, e.g. 'MOCK_LOCATION_DETECTED', 'LOCATION_DISABLED', 'PERMISSION_LOST'.
  final String alertType;

  /// Timestamp in milliseconds when the alert was triggered.
  final int timestamp;

  /// Additional details or metadata associated with the alert.
  final Map<String, dynamic> details;

  const SecurityAlert({
    required this.alertType,
    required this.timestamp,
    this.details = const {},
  });

  bool get isMockLocation => alertType == mockLocationDetected;
  bool get isLocationDisabled => alertType == locationDisabled;
  bool get isPermissionLost => alertType == permissionLost;

  factory SecurityAlert.fromMap(Map<dynamic, dynamic> map) {
    return SecurityAlert(
      alertType: map['alertType'] as String? ?? 'UNKNOWN',
      timestamp: map['timestamp'] as int? ?? DateTime.now().millisecondsSinceEpoch,
      details: (map['details'] as Map<dynamic, dynamic>?)?.cast<String, dynamic>() ?? {},
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'alertType': alertType,
      'timestamp': timestamp,
      'details': details,
    };
  }

  @override
  String toString() =>
      'SecurityAlert(alertType: $alertType, timestamp: $timestamp, details: $details)';
}
