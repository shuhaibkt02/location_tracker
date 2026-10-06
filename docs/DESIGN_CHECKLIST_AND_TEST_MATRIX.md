# Design Checklist & QA Test Matrix: Workforce Location Tracker

**Project:** `location_tracker` Flutter Plugin  
**Target:** Android 8.0 (API 26) through Android 15 (API 35+)  
**Purpose:** Generic, battery-efficient, tamper-resistant salesperson & workforce odometer tracking.

---

## Part 1: Design Checklist (The 28 Risk Categories)

### 1. Process & Service Lifecycle Resilience
- [ ] **Config Persistence for `START_STICKY`:** Persist `TrackingConfig` to disk (`SharedPreferences`) on start. When OS restarts service with `intent == null`, reload persisted config rather than failing.
- [ ] **Crash Loop Breaker:** Maintain a restart counter with timestamp; if service crashes $> 3$ times within 60 seconds, halt auto-restart to prevent battery draining loop.
- [ ] **`onTaskRemoved` Handling:** When user swipes host app from Recents, verify service continues running if tracking is active (`stopWithTask="false"`).
- [ ] **Graceful Teardown:** When `stopTracking()` is called, cleanly release `fusedLocationClient.removeLocationUpdates()`, unregister receivers, dismiss notification, and call `stopForeground(STOP_FOREGROUND_REMOVE)` before `stopSelf()`.
- [ ] **Android 14+ Foreground Service Enforcement:** Declare `android:foregroundServiceType="location"` in manifest. Call `ServiceCompat.startForeground` with location type flags immediately in `onCreate()` or `onStartCommand()`.

### 2. Permissions, Privacy & Compliance
- [ ] **Play Store Privacy Shield:** Do **not** request `ACCESS_BACKGROUND_LOCATION`. Rely entirely on `FOREGROUND_SERVICE_LOCATION` + `ACCESS_FINE_LOCATION`.
- [ ] **Android 13+ Notification Permission:** Check `POST_NOTIFICATIONS` before launching foreground notification; provide clear fallback or prompt if denied.
- [ ] **Runtime Permission Loss Detection:** Register callback or check permissions before requesting location updates. If permission is revoked mid-shift, pause tracking, notify Flutter via `EventChannel`, and post a warning notification.
- [ ] **Data Minimization:** Store only timestamp, coordinate, accuracy, and daily accumulated distance. Never log plaintext coordinates in production logs.

### 3. Odometer & Drift Protection (Kalman + Gating)
- [ ] **Speed Gating:** Discard updates where computed speed $< 0.8\text{ m/s}$ ($\sim 2.9\text{ km/h}$) unless sustained displacement exceeds $15\text{ meters}$.
- [ ] **Accuracy Gating:** Discard GPS points with accuracy $> 30\text{ meters}$ (configurable up to $50\text{m}$).
- [ ] **Kalman Filter Smoothing:** Pass raw coordinates through 1D/2D Kalman filter to reject multipath noise and GPS jitter while stationary.
- [ ] **First-Fix Discard:** Discard the first GPS fix after a cold start if accuracy is uncertain or timestamp is stale ($> 10\text{s}$ old).
- [ ] **Mock Location Detection:** Check `Location.isMock()` / `LocationCompat.isMock()` and drop spoofed coordinates with audit flag.

### 4. OEM & Battery Optimization
- [ ] **Eliminate Permanent WakeLocks:** Remove static 10-minute partial CPU WakeLocks. FusedLocationProvider hardware batching handles wakefulness.
- [ ] **Adaptive Polling:**
  - *Active Moving State:* Poll every 8–10 seconds.
  - *Stationary State ($> 3\text{ min}$ without movement):* Throttle polling to 30–60 seconds.
- [ ] **OEM Guidance API:** Expose a method `requestIgnoreBatteryOptimizations()` or provide OEM intent helpers (Auto-start for Xiaomi, Oppo, Vivo, Samsung).

### 5. Shift Rules & Midnight Rollover
- [ ] **Exact Alarm Elimination:** Do **not** use `AlarmManager.setExactAndAllowWhileIdle()` which triggers `SecurityException` without `SCHEDULE_EXACT_ALARM`.
- [ ] **Internal Handler / WorkManager Rollover:** While the foreground service is active, schedule daily midnight rollover via a lightweight `Handler.postDelayed` or calculate midnight delta inside the service loop.
- [ ] **Timezone & Clock Change Defense:** Use monotonic time (`SystemClock.elapsedRealtime()`) for speed and interval calculations; use UTC/ISO date strings for daily database keys.

### 6. Flutter <-> Native Bridge Contract
- [ ] **Unified Channel Names:** MethodChannel and EventChannel name strictly standardized to `com.harmonyloop.location_tracker`.
- [ ] **Typed Config & Models:** Flutter models (`TrackingConfig`, `LocationPoint`, `TrackingStatus`, `DailySummary`) with null-safety and default parameters.
- [ ] **EventChannel Lifecycle:** Support multiple attaches/detaches (e.g. Flutter Hot Reload, Hot Restart, background engine detachment) without leaking native listeners or crashing.
- [ ] **Clean Exception Hierarchy:** Throw typed `LocationTrackerException` instead of raw strings.

### 7. Notification & Tap-to-Open UX
- [ ] **Dynamic Intent Resolution:** Use `context.packageManager.getLaunchIntentForPackage(context.packageName)` so any host app opens automatically on notification tap.
- [ ] **Flag Immutability:** Always apply `PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE`.
- [ ] **Fallback Icon:** If host app does not supply a custom icon resource, fallback cleanly to `android.R.drawable.ic_menu_mylocation` without crashing.
- [ ] **Status Template:** Support parameterized text (e.g. `"Distance: {distance} km • {status}"`).

### 8. Data Persistence & Integrity
- [ ] **Single Source of Truth:** `DistanceStorage` manages writes; eliminate conflicting delta-overwriting calls from repository.
- [ ] **Structured Room Transactions:** Run database updates on a dedicated background dispatcher (`Dispatchers.IO`) under `SupervisorJob()`.
- [ ] **7-Day Rolling Retention:** Clean old days ($> 7$ days) strictly on daily rollover, not on every GPS coordinate.

---

## Part 2: QA & Verification Test Matrix

### Test Tier 1: Automated Unit & Concurrency Tests

| Test ID | Component | Scenario | Expected Behavior |
| :--- | :--- | :--- | :--- |
| **UT-01** | `KalmanFilter` | Stationary GPS jitter ($10\text{m}$ bouncing around same spot) | Filter converges; output coordinates remain stationary. |
| **UT-02** | `KalmanFilter` | Sudden $2\text{km}$ GPS teleportation outlier | Outlier rejected; distance accumulator unchanged. |
| **UT-03** | `LocationRepository` | Walking sequence ($1.2\text{ m/s}$ over $100\text{m}$) | Distance accumulated matches real displacement $\pm 5\%$. |
| **UT-04** | `DistanceStorage` | Concurrent writes from service and plugin | Thread-safe; total distance monotonically increases. |
| **UT-05** | `TrackingConfig` | Missing optional fields from Flutter | Fallbacks applied cleanly; no `NullPointerException`. |

---

### Test Tier 2: Android Lifecycle & OS Kill Scenarios

| Test ID | Scenario | Execution Steps | Pass Criteria |
| :--- | :--- | :--- | :--- |
| **LC-01** | **Swipe App from Recents** | Start tracking $\rightarrow$ open Recents $\rightarrow$ swipe app away. | Service stays active; notification remains; distance continues tracking. |
| **LC-02** | **OS Low Memory Kill** | Start tracking $\rightarrow$ run `am kill <package>` or memory stressor. | Service restarts via `START_STICKY`; recovers persisted config; continues tracking. |
| **LC-03** | **Flutter Hot Restart** | Start tracking $\rightarrow$ trigger Flutter Hot Restart in IDE. | Native service unaffected; EventChannel re-binds cleanly without crash. |
| **LC-04** | **Reboot While Tracking** | Start tracking $\rightarrow$ reboot device. | Tracking state persisted; clean start state (or boot receiver if configured). |
| **LC-05** | **Mid-Shift Permission Revoke** | Start tracking $\rightarrow$ revoke Location in OS Settings. | Service stops GPS requests gracefully; shows notification warning; no crash. |

---

### Test Tier 3: OEM & Battery Optimization Matrix

| OEM & OS | Test Focus | Verification Target |
| :--- | :--- | :--- |
| **Google Pixel (Android 14/15)** | Foreground Service Type Location | Starts without `ForegroundServiceStartNotAllowedException`. |
| **Samsung One UI (Android 13/14)** | Sleeping Apps & Battery Optimization | Service runs $> 2$ hours with screen locked in pocket. |
| **Xiaomi MIUI / HyperOS** | Auto-start & Aggressive Memory Management | Notification remains visible; no frozen location updates. |
| **Oppo / Realme ColorOS** | Background freeze & App Quick Freeze | GPS intervals maintained per config. |

---

### Test Tier 4: Field & Sales Rep Movement Scenarios

| Test ID | Scenario | Simulation / Real-World Steps | Pass Criteria |
| :--- | :--- | :--- | :--- |
| **FIELD-01** | **Desk / Office Meeting (3 hrs)** | Phone stationary on office desk with indoor GPS noise. | Accumulated distance $< 50\text{ meters}$ total over 3 hours. |
| **FIELD-02** | **Walking Client Visits** | Walk $1.5\text{ km}$ visiting 3 shops. | Accumulated distance matches Google Maps timeline $\pm 5\%$. |
| **FIELD-03** | **Vehicle / Highway Travel** | Drive $25\text{ km}$ at varying speeds ($20-100\text{ km/h}$). | Smooth tracking; accurate odometer; no discarded valid points. |
| **FIELD-04** | **Tunnel / Underground Parking** | Drive through tunnel ($0$ GPS satellites for $2\text{ min}$). | No crash; resumes seamlessly upon exit without false jump. |
| **FIELD-05** | **Midnight Rollover** | Track across 23:59:50 to 00:00:10. | Previous day archived; today's odometer resets to $0.00\text{ m}$. |
