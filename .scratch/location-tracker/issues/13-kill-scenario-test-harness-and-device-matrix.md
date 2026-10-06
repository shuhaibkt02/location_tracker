# 13: Kill-Scenario Test Harness & Device Matrix

**What to build:** An automated test harness script and QA validation matrix to verify tracking resilience against OS process kills, app swipe-away, Doze mode battery optimization, device reboots, and OEM-specific aggressive killers.

**Blocked by:** 08: Process Death Recovery, Boot Receiver, and Rehydration, 09: In-Service Shift Rollover (Midnight / Custom End), 11: OEM Battery Helpers & Guidance UI

**Status:** ready-for-agent

- [ ] Automated bash/ADB test script (`scripts/test_kill_scenarios.sh`) simulating:
  - App swipe-away from recents while tracking is active.
  - Low-memory killer simulation via `adb shell am kill <package>`.
  - Android Doze mode transition via `adb shell dumpsys deviceidle force-idle`.
  - Boot broadcast simulation via `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED`.
- [ ] Automated verification asserts that the foreground service restarts and distance tracking continues without odometer reset.
- [ ] Device test matrix document with step-by-step verification procedures for Samsung One UI, Xiaomi HyperOS, Oppo ColorOS, and Google Pixel (Android 14/15).
- [ ] End-to-end field test verification runbook checking stationary office drift vs. active walking routes.
