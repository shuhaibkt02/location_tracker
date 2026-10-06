# 20: Enum & Contract Alignment (TrackingStatus & DailyDistance)

**What to build:** Native Kotlin enums, Dart models, and test fixtures are fully aligned against the canonical specification contracts, eliminating missing enum members and broken test expectations.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] `TrackingStatus` enum in both Kotlin and Dart defines identical members: `STATIONARY`, `MOVING`, `PAUSED`, `STOPPED`, `PERMISSION_REVOKED`, `RESUMED`, and `UNKNOWN`.
- [x] `DailyDistance` model exposes calculated helper `double get distanceKm => distanceMeters / 1000.0;` matching test assertions.
- [x] In `LocationService.kt`, service class name and file structure align cleanly (`LocationTrackingService` or `DistanceTrackingService`).
- [x] Dart unit tests in `test/location_tracker_test.dart` pass 100% without unresolved symbols or missing enum values.
