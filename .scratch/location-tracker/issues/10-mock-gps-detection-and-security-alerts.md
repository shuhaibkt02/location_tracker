# 10: Mock-GPS Detection & Security Alerts

**What to build:** The tracking pipeline detects fake GPS / mock location apps, drops spoofed coordinates from travel claims, and alerts the Flutter host application so compliance/audit logs can be reported to management.

**Blocked by:** 05: Anti-Drift Engine: Kalman Filter & Speed Gating

**Status:** closed

- [x] Every incoming location is inspected via `LocationCompat.isMock(location)`.
- [x] If mock location is detected, the coordinate is immediately rejected and not added to the odometer displacement.
- [x] Dart API exposes `LocationTracker.onSecurityAlert` emitting `Stream<SecurityAlert>`.
- [x] A security event with type `MOCK_LOCATION_DETECTED`, provider, and timestamp is emitted over the event channel (ADR-008).
- [x] Debug configuration flag `allowMockLocationsInDebug` allows developer testing with simulated mock locations without triggering security drops when in debug mode.

