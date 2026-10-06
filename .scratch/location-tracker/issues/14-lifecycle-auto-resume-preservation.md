# 14: Lifecycle Fix: Preserve is_tracking Across onDestroy

**What to build:** When the Android OS terminates `DistanceTrackingService` under low-memory pressure or when the host application task is swiped away from recent apps, the persisted active tracking flag (`is_tracking_active`) is retained rather than wiped. Only explicit calls to `stopTracking()` or automated shift completion deactivate tracking state, allowing `START_STICKY`, `BootCompletedReceiver`, and app re-launches to reliably rehydrate configuration and resume active shifts.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] `DistanceTrackingService.onDestroy()` distinguishes between intentional stops (via `stopTracking()` / auto-stop) and OS teardown (process kill / low memory), ensuring `ConfigStorage.setTrackingActive(false)` is only invoked on intentional teardown.
- [x] `LocationRepository.isTracking` lifecycle state mirrors persistent tracking status across process recreation.
- [x] Swiping away the host app leaves `is_tracking_active == true` in persistent storage.
- [x] Launching the host app when `is_tracking_active == true` automatically re-establishes foreground tracking without requiring the user to re-start manually (ADR-006).
