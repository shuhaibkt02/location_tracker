# 03: FGS Lifecycle, Tap-to-Open, and Config Persistence

**What to build:** The host app can pass a `TrackingConfig` to start a persistent Android Foreground Service. The user sees an ongoing status bar notification with custom text/icon that, when tapped, brings the host Flutter app to the foreground. The host app can stop tracking cleanly.

**Blocked by:** 02: Permissions, Rationale UI, and Denial/Revocation Handling

**Status:** ready-for-agent

- [ ] Dart API `LocationTracker.startTracking(TrackingConfig config)` accepts custom notification title, body, icon resource name, update interval, and notification channel parameters.
- [ ] Native `DistanceTrackingService` starts as `foregroundServiceType="location"` via `ServiceCompat.startForeground`.
- [ ] Notification tap intent is resolved dynamically using `packageManager.getLaunchIntentForPackage(packageName)` with `PendingIntent.FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` (ADR-005).
- [ ] Dart API `LocationTracker.stopTracking()` gracefully tears down location updates, cancels notifications, and calls `stopForeground(STOP_FOREGROUND_REMOVE)` before stopping the service.
- [ ] Calling `LocationTracker.isTracking()` returns accurate tracking state from memory/service.
- [ ] `TrackingConfig` is serialized and saved to `SharedPreferences` upon start for recovery persistence.
