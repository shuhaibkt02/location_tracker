# 16: Room Persistence Fallback & In-Memory Native Unit Tests

**What to build:** `LocationTracker.getTodayDistance()` reliably returns today's traveled distance from local Room database storage even if the tracking service is currently stopped or killed. The Room database layer is covered by in-memory SQLite JVM unit tests asserting atomic UPSERTs, date retrieval, and retention pruning.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] File `localDatabase.kt` is renamed to PascalCase `LocalDatabase.kt` in accordance with Kotlin coding standards.
- [ ] `getTodayDistance` / `getTotalDistance` method channel handlers query Room database directly when the in-memory singleton is uninitialized or reads $0.0\text{ m}$.
- [ ] In-memory Room database unit tests (`Room.inMemoryDatabaseBuilder`) verify:
  - Inserting and updating today's distance replaces existing records without race conditions.
  - Querying today's record returns accurate distance.
  - Querying history returns correctly ordered records up to the requested limit.
  - Pruning old records retains only the last 7 calendar days.
