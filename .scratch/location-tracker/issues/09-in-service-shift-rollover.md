# 09: In-Service Shift Rollover (Midnight / Custom End)

**What to build:** The active shift odometer automatically resets or ends at midnight (or at a configured shift end time like 18:00) using safe in-service timing mechanisms without triggering Android 12+ exact alarm crashes.

**Blocked by:** 03: FGS Lifecycle, Tap-to-Open, and Config Persistence

**Status:** ready-for-agent

- [ ] `TrackingConfig` supports `enableAutoStop` (bool), `autoStopHour` (int, default 0), and `autoStopMinute` (int, default 0).
- [ ] Active service schedules daily rollover using internal `Handler.postDelayed` or timestamp verification on location ticks, eliminating `AlarmManager.setExactAndAllowWhileIdle` and `SCHEDULE_EXACT_ALARM` permissions (ADR-004).
- [ ] At rollover: yesterday's final distance is finalized in Room DB, the active odometer resets to $0.00\text{ m}$, and a new daily entry is initialized.
- [ ] If `enableAutoStop` is true, the service gracefully terminates and posts a "Shift Completed" notification.
- [ ] Unit tests verify rollover triggers correctly across artificial clock advances.
