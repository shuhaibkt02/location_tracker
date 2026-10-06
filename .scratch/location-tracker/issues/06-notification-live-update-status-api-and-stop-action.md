# 06: Notification Live-Update, Status API, and Stop Action

**What to build:** The foreground notification dynamically displays the sales rep's live traveled distance in the status bar. The host app can observe motion state (`MOVING`, `STATIONARY`, `PAUSED`) and optionally enable or disable an interactive status bar stop button.

**Blocked by:** 05: Anti-Drift Engine: Kalman Filter & Speed Gating

**Status:** done

- [x] Status bar notification dynamically refreshes body text based on configured template string (e.g. `"Distance: {distance} km • {status}"`) as movement occurs.
- [x] Adaptive notification throttler prevents excessive notification posting (updates posted at most once every 3–5 seconds or upon $\ge 10\text{m}$ movement).
- [x] Dart API exposes `LocationTracker.onStatusChanged` emitting `Stream<TrackingStatus>` (`STATIONARY`, `MOVING`, `PAUSED`).
- [x] Dart API `LocationTracker.getTodayDistance()` returns current accumulated distance in meters.
- [x] Config field `enableNotificationStopButton` (default `false` for anti-tamper per ADR-007) optionally adds an interactive "Stop Tracking" action button to the notification tray.
- [x] Tapping the notification stop action stops tracking and cleans up the notification.
