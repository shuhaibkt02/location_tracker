# 22: Regression Suite: Lifecycle Preservation, Rollover & Boot Recovery

**What to build:** An automated test harness suite validating the critical edge-case fixes from Tickets 14 and 15, asserting that service auto-resume survives process kills, device reboots rehydrate saved shifts, and artificial midnight clock transitions segregate daily totals cleanly.

**Blocked by:** 14: Lifecycle Fix: Preserve is_tracking Across onDestroy, 15: Shift Rollover Date-Boundary Fix & Shared Date Utility

**Status:** completed

- [x] Automated regression tests verify `is_tracking_active` remains true in `ConfigStorage` following simulated service destruction without explicit stop intent.
- [x] Automated regression tests verify `performShiftRollover` archives yesterday's total under yesterday's date and resets the odometer to 0.00 m on artificial date advances.
- [x] Test script `scripts/test_kill_scenarios.sh` is enhanced with a scenario verifying `am kill` followed immediately by `BOOT_COMPLETED` rehydrates configuration and restores the foreground service.
- [x] End-to-end assertions confirm odometer continuity across the complete kill-revive cycle without phantom drift.
