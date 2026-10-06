# Architecture Decision Records (ADRs)

**Project:** `location_tracker` Flutter Plugin  
**Domain:** Enterprise Workforce & Salesperson Mobility Tracking  
**Status:** Canonical Design Decisions

---

### ADR-001: Foreground Service (`location`) vs. Background Location Permission

* **Status:** Accepted
* **Context:**
  Sales representatives require continuous odometer tracking during work shifts. Android 10+ introduced `ACCESS_BACKGROUND_LOCATION`, which requires separate OS permission dialogs and triggers strict Google Play Store manual policy reviews with mandatory video submissions. Android 14+ enforces explicit foreground service types.
* **Decision:**
  Rely exclusively on `android.permission.FOREGROUND_SERVICE_LOCATION` paired with `ACCESS_FINE_LOCATION` and an ongoing persistent notification. Omit `ACCESS_BACKGROUND_LOCATION`.
* **Consequences:**
  - **Positive:** Bypasses Google Play Background Location policy scrutiny; 100% compliant with Android 14/15; high tracking fidelity while user sees active notification.
  - **Negative:** Tracking is visibly tied to an ongoing status bar notification; tracking stops if user or OS terminates the foreground service.

---

### ADR-002: Process Death Recovery via `START_STICKY` & Disk Config Persistence

* **Status:** Accepted
* **Context:**
  Low-memory devices or aggressive OEM battery managers can kill the app process. When Android restarts a service returned with `START_STICKY`, the restart intent is `null`, losing all memory-resident configuration passed from Flutter.
* **Decision:**
  When `startTracking(config)` is invoked:
  1. Serialize `TrackingConfig` into JSON and persist to `SharedPreferences`.
  2. Mark `is_tracking_active = true` in persistent storage.
  3. When `onStartCommand` receives `intent == null`, re-hydrate `TrackingConfig` from `SharedPreferences`.
* **Consequences:**
  - **Positive:** Seamless service recovery after OS low-memory kills without requiring Flutter engine re-launch.
  - **Negative:** Need careful synchronization when `stopTracking()` is called to ensure `is_tracking_active` is reset to false.

---

### ADR-003: GPS Drift Elimination via Kalman Filtering and Speed Gating

* **Status:** Accepted
* **Context:**
  Sales reps spend significant portions of their day inside offices, retail shops, or cafes. Multipath GPS reflections cause coordinates to bounce randomly by $10-50\text{m}$, creating "phantom mileage" (sales reps unfairly claiming mileage reimbursement while sitting still).
* **Decision:**
  Implement a two-stage filter:
  1. **Speed & Accuracy Gate:** Discard raw fixes with horizontal accuracy $> 35\text{m}$ or calculated velocity $< 0.8\text{ m/s}$ ($\sim 2.9\text{ km/h}$).
  2. **1D/2D Kalman Filter:** Feed accepted fixes into a Kalman filter that predicts and updates position estimates based on measurement covariance and process noise.
* **Consequences:**
  - **Positive:** Eradicates stationary GPS drift; produces highly credible travel claims.
  - **Negative:** May slightly truncate initial 2–3 meters when accelerating from a dead stop.

---

### ADR-004: In-Service Midnight & Shift Rollover (Eliminating Exact Alarms)

* **Status:** Accepted
* **Context:**
  The previous implementation used `AlarmManager.setExactAndAllowWhileIdle()` to trigger a midnight receiver. On Android 12+ (API 31+), exact alarms crash with `SecurityException` unless the user grants a special system setting (`SCHEDULE_EXACT_ALARM`), which Google Play restricts.
* **Decision:**
  Eliminate `AlarmManager` for active tracking sessions. Since the foreground service is already running with an active event loop, compute the milliseconds remaining until shift end / midnight and schedule rollover via an internal `Handler.postDelayed()` or timestamp check on incoming GPS fixes.
* **Consequences:**
  - **Positive:** Zero permission crashes on Android 12–15; zero Google Play exact alarm review friction.
  - **Negative:** Relies on the service remaining alive; if killed right at midnight, the next restart handles rollover on its first location fix by checking the date key.

---

### ADR-005: Generic Host App Integration via Dynamic Launcher Intent

* **Status:** Accepted
* **Context:**
  This is a generic plugin distributed via pub.dev / private repo. Hardcoding host app package names or activity classes breaks portability across different host projects.
* **Decision:**
  Resolve the host app's main launcher activity at runtime using:
  ```kotlin
  val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
  val pendingIntent = PendingIntent.getActivity(
      context, 0, launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )
  ```
* **Consequences:**
  - **Positive:** Works in 100% of Flutter host apps with zero custom native XML configuration.
  - **Negative:** Tapping notification opens the main Flutter activity rather than deep-linking to a sub-screen.

---

### ADR-006: Auto-Resume Interrupted Session on App Re-Launch

* **Status:** Accepted
* **Context:**
  Android OS force-stops, low-memory kills, or device reboots will terminate the foreground tracking service. When the sales representative reopens the host application, they should not have to manually remember to re-start tracking, nor should they lose their ongoing shift odometer.
* **Decision:**
  During plugin attachment to the Flutter engine:
  1. Inspect persistent storage for `is_tracking_active == true`.
  2. If active, automatically trigger `startForegroundService` with the persisted `TrackingConfig`.
  3. Emit a status event `TrackingStatus.RESUMED` over the `EventChannel` to inform the Flutter host app that tracking resumed.
* **Consequences:**
  - **Positive:** Uninterrupted shift tracking and zero data loss after crashes, reboots, or task kills.
  - **Negative:** If the user intentionally stopped tracking via an external mechanism without calling `stopTracking()`, the service will resume until explicitly stopped or midnight cutoff fires.

---

### ADR-007: Tamper-Resistant Notification (No Status Bar Stop Action)

* **Status:** Accepted
* **Context:**
  In field sales and workforce management, employees might intentionally or inadvertently tap an action button in the status bar notification to stop tracking during their shift, causing lost mileage and compliance disputes.
* **Decision:**
  The foreground service notification will be informational and interactive only for navigation (tapping anywhere on the notification brings the host Flutter app to the foreground). No "Stop Tracking" or "Pause" action buttons will be exposed in the notification tray. All session lifecycle controls must be driven exclusively from the authenticated Flutter host app UI.
* **Consequences:**
  - **Positive:** Tamper-resistant tracking during shifts; eliminates accidental disconnections; guarantees that stopping a shift is an intentional, auditable action inside the business application.
  - **Negative:** Reps must open the app to conclude their shift if auto-shift cutoff has not yet arrived.

---

### ADR-008: Mock Location Discard & Fraud Detection Audit

* **Status:** Accepted
* **Context:**
  Sales reps in the field may attempt to use mock location provider apps (Fake GPS) to fabricate client visits or claim fraudulent travel allowances.
* **Decision:**
  On every incoming coordinate update, check `LocationCompat.isMock(location)`. If detected:
  1. Immediately discard the point from distance accumulation.
  2. Emit a `SecurityAlert` event (`type: "MOCK_LOCATION_DETECTED"`, timestamp, provider) over the Flutter `EventChannel`.
  3. Log an audit entry so the host application can record the incident and report it to backend compliance/HR.
* **Consequences:**
  - **Positive:** Protects business travel claims from mileage fraud; provides real-time anti-tampering signals.
  - **Negative:** Automated testing using mock GPS coordinates requires a debug configuration toggle or emulator test mode.

---

### ADR-009: Integrated OEM Battery Saver Guidance & Exemption Helpers

* **Status:** Accepted
* **Context:**
  Aggressive OEM battery managers (Xiaomi MIUI/HyperOS, Samsung One UI, Oppo ColorOS, Huawei EMUI) frequently freeze or kill background services after 15–30 minutes unless the user explicitly exempts the app from battery restrictions or grants autostart. Requiring host applications to install separate packages creates fragmented UX.
* **Decision:**
  Incorporate native battery optimization helpers directly in the plugin:
  1. `isIgnoringBatteryOptimizations(): Future<bool>`
  2. `requestIgnoreBatteryOptimizations(): Future<bool>` (invokes `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
  3. `openOemBatterySettings(): Future<bool>` (navigates to manufacturer-specific autostart/battery managers on Xiaomi, Samsung, Oppo, Vivo, Huawei).
* **Consequences:**
  - **Positive:** Direct, out-of-the-box solution to the #1 cause of background service kills in emerging mobile markets; simplifies host app implementation.
  - **Negative:** Google Play requires apps requesting battery optimization exemptions to have a core functionality that genuinely requires background operation (such as workforce tracking/navigation).
