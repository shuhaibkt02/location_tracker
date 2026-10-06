# 18: Security Alerts Pipeline (LOCATION_DISABLED, PERMISSION_LOST) & Typed Events

**What to build:** The tracking pipeline detects mid-shift security and integrity disruptions—specifically when system location is turned off by the user (`LOCATION_DISABLED`) or location/notification permissions are revoked mid-tracking (`PERMISSION_LOST`)—and emits real-time security alerts over `LocationTracker.onSecurityAlert`. The native event bus utilizes typed event models instead of raw strings.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `LocationEventBus` uses typed enums/data classes (`SecurityAlertType`, `TrackingStatus`) rather than unvalidated raw strings and unstructured maps.
- [ ] If system location services (GPS/Network) are disabled mid-tracking, the service detects the provider shutdown and emits a `LOCATION_DISABLED` alert on the event stream.
- [ ] If runtime location or notification permissions are revoked mid-shift, the service detects the revocation, notifies the user via status bar warning, and emits a `PERMISSION_LOST` security alert over the event channel.
- [ ] Dart API `SecurityAlert` parses `alertType` cleanly for all three variants: `MOCK_LOCATION_DETECTED`, `LOCATION_DISABLED`, and `PERMISSION_LOST`.
