# 08: Process Death Recovery, Boot Receiver, and Rehydration

**What to build:** If the Android OS terminates the tracking service under memory pressure, `START_STICKY` re-hydrates the saved configuration from disk and resumes tracking. When the device reboots, an optional `BOOT_COMPLETED` receiver resumes tracking if configured. When the host app launches, any interrupted active shift resumes automatically.

**Blocked by:** 07: Room Persistence & 7-Day History

**Status:** done

- [x] When `DistanceTrackingService.onStartCommand` receives `intent == null` (OS restart), it reloads the persisted `TrackingConfig` and restores tracking seamlessly.
- [x] Crash loop circuit breaker: if the service crashes $\ge 3$ times within 60 seconds, auto-recovery halts to prevent battery-draining reboot loops.
- [x] Plugin checks persistent state on Flutter engine attach; if `is_tracking_active == true`, tracking auto-resumes and emits `TrackingStatus.RESUMED` (ADR-006).
- [x] `BootCompletedReceiver` declared in manifest with `RECEIVE_BOOT_COMPLETED` permission; restores active tracking on device reboot if `autoResumeOnBoot` is enabled in config.
- [x] Verifiable via ADB: `adb shell am kill <package>` restarts the service and recovers active distance.
