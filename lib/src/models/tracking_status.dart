/// Movement and operational status of the location tracker.
enum TrackingStatus {
  /// Employee is stationary (speed below threshold or inside office/building).
  stationary,

  /// Employee is actively in transit (walking or driving).
  moving,

  /// Stationary for extended period (> 3 minutes); GPS polling throttled.
  paused,

  /// Location permission was revoked while tracking was active.
  permissionRevoked,

  /// Active tracking was auto-resumed after process kill or reboot.
  resumed,

  /// Status unrecognized.
  unknown;

  static TrackingStatus fromString(String? value) {
    switch (value?.toUpperCase()) {
      case 'STATIONARY':
        return TrackingStatus.stationary;
      case 'MOVING':
        return TrackingStatus.moving;
      case 'PAUSED':
        return TrackingStatus.paused;
      case 'PERMISSION_REVOKED':
        return TrackingStatus.permissionRevoked;
      case 'RESUMED':
        return TrackingStatus.resumed;
      default:
        return TrackingStatus.unknown;
    }
  }
}
