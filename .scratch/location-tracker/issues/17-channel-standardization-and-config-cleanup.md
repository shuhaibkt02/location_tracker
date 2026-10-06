# 17: Channel Standardization, Config Parsing Cleanup, and Dead Code Removal

**What to build:** Communication channels are strictly standardized to the unified project namespace, configuration map parsing is encapsulated within `TrackingConfigData`, and unused dead code is removed from the codebase.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] MethodChannel identifier is updated to `com.harmonyloop.location_tracker` across Dart and Kotlin implementations.
- [x] EventChannel identifier is updated to `com.harmonyloop.location_tracker/events` across Dart and Kotlin implementations.
- [x] `TrackingConfigData.fromMap(map: Map<String, Any?>)` encapsulates configuration dictionary unpacking, eliminating Feature Envy in `LocationTrackerPlugin.kt`.
- [x] Unused method `setProcessNoise` in `KalmanFilter.kt` is removed.
- [x] Unit tests verify configuration serialization and deserialization with default fallbacks.
