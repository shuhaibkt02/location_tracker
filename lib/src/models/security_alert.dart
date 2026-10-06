/// Represents a security violation or tamper attempt detected during tracking.
class SecurityAlert {
  /// The type of alert, e.g. 'MOCK_LOCATION_DETECTED'.
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
