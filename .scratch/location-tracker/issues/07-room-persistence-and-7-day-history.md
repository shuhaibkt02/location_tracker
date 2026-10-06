# 07: Room Persistence & 7-Day History

**What to build:** Daily travel distance is atomically persisted into a local Room SQLite database keyed by calendar date (`yyyy-MM-dd`). Concurrency race conditions are eliminated, and the host app can query the past 7 days of mileage history for expense claims.

**Blocked by:** 04: EventChannel Streaming & Hot-Restart Safety

**Status:** ready-for-agent

- [ ] Room database `AppDatabase` manages `DailyDistanceEntity` with atomic UPSERT queries on date key (`yyyy-MM-dd`).
- [ ] Conflicting incremental delta writes from `LocationRepository` are eliminated; single source of truth stores cumulative daily distance.
- [ ] Concurrency uses a structured `CoroutineScope(SupervisorJob() + Dispatchers.IO)` without orphan coroutines.
- [ ] Database pruning (`DELETE WHERE date NOT IN (...)`) runs strictly once per daily rollover, not on every GPS coordinate.
- [ ] Dart API `LocationTracker.getDailyHistory({int days = 7})` returns a list of `DailyDistance` records (`date`, `distanceMeters`).
