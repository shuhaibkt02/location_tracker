/// Complete configuration model for workforce location tracking.
class TrackingConfig {
  /// Title shown in the foreground status bar notification.
  final String notificationTitle;

  /// Parameterized template for notification body text (e.g. "Distance: {distance} km • {status}").
  final String notificationBodyTemplate;

  /// Custom Android drawable icon resource name (falls back to system default).
  final String? notificationIconResource;

  /// Android notification channel ID.
  final String notificationChannelId;

  /// User-visible Android notification channel name.
  final String notificationChannelName;

  /// Active GPS polling interval in milliseconds.
  final int updateIntervalMs;

  /// Minimum displacement distance in meters to register a location delta.
  final double minDistanceFilterMeters;

  /// Speed threshold in meters per second (default 0.8 m/s ~ 2.9 km/h) to filter indoor jitter.
  final double speedThresholdMps;

  /// Discard location fixes with horizontal accuracy worse than this threshold in meters.
  final double accuracyFilterMeters;

  /// Whether to automatically stop or rollover tracking at shift end.
  final bool enableAutoStop;

  /// Hour of the day (0-23) to trigger auto-stop/rollover (default 0 for midnight).
  final int autoStopHour;

  /// Minute of the hour (0-59) to trigger auto-stop/rollover (default 0).
  final int autoStopMinute;

  /// Whether to display an interactive "Stop Tracking" action in the notification tray (default false for anti-tamper).
  final bool enableNotificationStopButton;

  /// Whether to auto-resume active shift tracking across device reboots.
  final bool autoResumeOnBoot;

  const TrackingConfig({
    this.notificationTitle = 'Workforce Tracking',
    this.notificationBodyTemplate = 'Distance: {distance} km • {status}',
    this.notificationIconResource,
    this.notificationChannelId = 'location_tracker_channel',
    this.notificationChannelName = 'Location Tracker Service',
    this.updateIntervalMs = 8000,
    this.minDistanceFilterMeters = 1.0,
    this.speedThresholdMps = 0.8,
    this.accuracyFilterMeters = 35.0,
    this.enableAutoStop = true,
    this.autoStopHour = 0,
    this.autoStopMinute = 0,
    this.enableNotificationStopButton = false,
    this.autoResumeOnBoot = true,
  });

  Map<String, dynamic> toMap() {
    return {
      'notificationTitle': notificationTitle,
      'notificationBodyTemplate': notificationBodyTemplate,
      'notificationIconResource': notificationIconResource,
      'notificationChannelId': notificationChannelId,
      'notificationChannelName': notificationChannelName,
      'updateIntervalMs': updateIntervalMs,
      'minDistanceFilterMeters': minDistanceFilterMeters,
      'speedThresholdMps': speedThresholdMps,
      'accuracyFilterMeters': accuracyFilterMeters,
      'enableAutoStop': enableAutoStop,
      'autoStopHour': autoStopHour,
      'autoStopMinute': autoStopMinute,
      'enableNotificationStopButton': enableNotificationStopButton,
      'autoResumeOnBoot': autoResumeOnBoot,
    };
  }

  factory TrackingConfig.fromMap(Map<dynamic, dynamic> map) {
    return TrackingConfig(
      notificationTitle:
          map['notificationTitle'] as String? ?? 'Workforce Tracking',
      notificationBodyTemplate: map['notificationBodyTemplate'] as String? ??
          'Distance: {distance} km • {status}',
      notificationIconResource: map['notificationIconResource'] as String?,
      notificationChannelId: map['notificationChannelId'] as String? ??
          'location_tracker_channel',
      notificationChannelName: map['notificationChannelName'] as String? ??
          'Location Tracker Service',
      updateIntervalMs: map['updateIntervalMs'] as int? ?? 8000,
      minDistanceFilterMeters:
          (map['minDistanceFilterMeters'] as num?)?.toDouble() ?? 1.0,
      speedThresholdMps:
          (map['speedThresholdMps'] as num?)?.toDouble() ?? 0.8,
      accuracyFilterMeters:
          (map['accuracyFilterMeters'] as num?)?.toDouble() ?? 35.0,
      enableAutoStop: map['enableAutoStop'] as bool? ?? true,
      autoStopHour: map['autoStopHour'] as int? ?? 0,
      autoStopMinute: map['autoStopMinute'] as int? ?? 0,
      enableNotificationStopButton:
          map['enableNotificationStopButton'] as bool? ?? false,
      autoResumeOnBoot: map['autoResumeOnBoot'] as bool? ?? true,
    );
  }
}
