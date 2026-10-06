# 07: Room Persistence & 7-Day History

**What to build:** Daily travel distance is atomically persisted into a local Room SQLite database keyed by calendar date (`yyyy-MM-dd`). Concurrency race conditions are eliminated, and the host app can query the past 7 days of mileage history for expense claims.

**Blocked by:** 04: EventChannel Streaming & Hot-Restart Safety

**Status:** done

- [x] Room database `AppDatabase` manages `DailyDistanceEntity` with atomic UPSERT queries on date key (`yyyy-MM-dd`).
- [x] Conflicting incremental delta writes from `LocationRepository` are eliminated; single source of truth stores cumulative daily distance.
- [x] Concurrency uses a structured `CoroutineScope(SupervisorJob() + Dispatchers.IO)` without orphan coroutines.
- [x] Database pruning (`DELETE WHERE date NOT IN (...)`) runs strictly once per daily rollover, not on every GPS coordinate.
- [x] Dart API `LocationTracker.getDailyHistory({int days = 7})` returns a list of `DailyDistance` records (`date`, `distanceMeters`).
