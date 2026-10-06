# 15: Shift Rollover Date-Boundary Fix & Shared Date Utility

**What to build:** The midnight shift rollover correctly records yesterday's accumulated distance under yesterday's calendar date (`yyyy-MM-dd`) before resetting the active odometer to zero under the new calendar date. A single shared date utility eliminates duplicate date formatting across the service and Room persistence layers.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Shared date utility `DateHelper.getFormattedDate(...)` provides standard ISO `yyyy-MM-dd` date strings, eliminating duplicate implementations in `localDatabase.kt` and `LocationService.kt`.
- [x] At midnight rollover, yesterday's cumulative odometer reading is explicitly saved to Room DB using yesterday's calendar date rather than today's date.
- [x] The active odometer in `LocationRepository` resets to $0.00\text{ m}$ for the new day, and today's initial record is initialized at $0.00\text{ m}$.
- [x] If continuous tracking is enabled (`enableAutoStop == false`), new location fixes arriving after midnight accumulate only into today's odometer.
