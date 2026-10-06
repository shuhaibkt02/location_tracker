# Specification: Generic Workforce & Salesperson Location Tracking Plugin

## Problem Statement

Enterprises employing field sales representatives, inspectors, and mobile workforce teams require accurate, indisputable mileage and location tracking for travel allowance reimbursement and proof-of-work. Currently, developers building Flutter workforce apps face critical challenges on Android:

1. **Stationary GPS Drift & Fraudulent Mileage:** When sales reps sit in meetings, offices, or indoor spaces for hours, GPS multipath jitter creates artificial movement, accumulating "phantom kilometers" and causing contentious reimbursement disputes.
2. **Aggressive OS & OEM Process Kills:** Battery managers from manufacturers such as Xiaomi, Samsung, and Oppo terminate background apps, while modern Android versions (Android 12–15) enforce strict restrictions on background service launches, exact alarms, and notification permissions.
3. **Play Store Rejection Risk:** Plugins requesting broad background location permissions (`ACCESS_BACKGROUND_LOCATION`) undergo stringent Google Play policy reviews that frequently result in app rejection.
4. **Poor Integration Flexibility:** Existing solutions either hardcode behavior (notification text, fixed shifts, specific layouts) or fail to provide a plug-and-play Flutter API where the host application can completely configure the tracking parameters, dynamic status bar display, and tap-to-open behavior.

---

## Solution

A production-grade, generic Flutter Android plugin (`location_tracker`) tailored for workforce mobility:

1. **Configurable Foreground Service:** The host Flutter application supplies a complete tracking configuration (`TrackingConfig`) specifying notification appearance, tracking intervals, and shift rules. A persistent foreground notification keeps the process alive under Android's highest priority level, completely bypassing Google Play background location policy friction.
2. **Dynamic Tap-to-Open Status Bar:** The ongoing status bar notification displays real-time travel distance and tracking status, and dynamically launches the host Flutter application whenever tapped.
3. **Anti-Drift Odometer Pipeline:** A 2D Kalman filter combined with speed gating ($\ge 0.8\text{ m/s}$) eliminates indoor stationary noise while calculating true physical displacement.
4. **Resilient Persistence & Process Death Recovery:** Daily accumulated distance is stored atomically in a local Room database. If the process is terminated by the OS, `START_STICKY` combined with disk configuration persistence automatically re-hydrates tracking, and active shifts auto-resume upon app relaunch.
5. **In-Service Shift Cutoff:** Daily rollover at midnight or custom shift end is handled safely inside the service lifecycle without requiring dangerous exact alarm permissions.
6. **Anti-Tamper & Fraud Defense:** Mock GPS locations are instantly discarded from mileage calculations and flagged to Flutter, while native battery optimization helpers guide employees to whitelist the app on aggressive OEM devices.

---

## User Stories

1. As a field sales representative, I want the app to track my travel distance in the background while my phone is in my pocket, so that I can automatically claim accurate mileage allowances at the end of the day.
2. As a field sales representative, I want GPS drift to be ignored when I am stationary in a client meeting, so that my mileage report reflects only actual travel and does not raise fraud suspicions.
3. As a field sales representative, I want to see my live accumulated distance in the Android status bar notification, so that I always know my tracking is active and can monitor my progress at a glance.
4. As a field sales representative, I want tapping the status bar notification to immediately bring my work app to the foreground, so that I can quickly check my itinerary or log a visit.
5. As an enterprise employer, I want tracking to automatically stop at the end of the shift or midnight, so that employee privacy is strictly respected outside of working hours.
6. As an enterprise payroll auditor, I want daily travel distances to be stored persistently for the past 7 days, so that mileage claims can be verified even if the device was offline.
7. As a compliance officer, I want simulated or mock GPS locations to be rejected and flagged, so that fraudulent mileage claims cannot be generated using Fake GPS tools.
8. As a sales operations manager, I want employees to be unable to sneakily stop tracking from the notification tray during shift hours, so that all shift terminations are deliberate and audited inside the app.
9. As a Flutter app developer, I want to configure the notification title, body template, channel name, and icon resource name from Dart, so that the plugin matches my company's branding seamlessly.
10. As a Flutter app developer, I want to configure the GPS polling interval and distance filter from Dart, so that I can optimize tracking granularity for walking versus vehicular teams.
11. As a Flutter app developer, I want to listen to a real-time stream of location coordinates via Dart, so that I can plot the sales rep's live route on an in-app map.
12. As a Flutter app developer, I want to listen to tracking status changes (`MOVING`, `STATIONARY`, `PAUSED`), so that my UI can display whether the rep is en route or at a client site.
13. As a Flutter app developer, I want hot reload and hot restart in my development environment to work without crashing the native background service or breaking event streams.
14. As a Flutter app developer, I want to query the past 7 days of daily distances with a single asynchronous method call, so that I can render weekly mileage summary charts.
15. As a mobile developer, I want the plugin to use foreground service location rights rather than requiring `ACCESS_BACKGROUND_LOCATION`, so that my app passes Google Play Store review without mandatory video submissions.
16. As a mobile developer, I want the plugin to handle Android 13+ `POST_NOTIFICATIONS` and Android 14+ `FOREGROUND_SERVICE_LOCATION` requirements cleanly, so that the app does not crash with runtime security exceptions.
17. As an enterprise support engineer, I want the plugin to maintain a circular diagnostic log that redacts exact GPS coordinates, so that field bugs can be investigated without violating employee privacy or GDPR laws.
18. As an enterprise support engineer, I want an API to export diagnostic logs to a file, so that field employees can attach diagnostics to support tickets.
19. As a field sales representative on a Xiaomi or Samsung phone, I want the app to guide me directly to OEM battery settings, so that my background tracking is not unexpectedly killed by OEM battery savers.
20. As a field sales representative, I want my active shift odometer to survive when the operating system kills the app due to low memory, so that I don't lose hours of logged travel.
21. As a field sales representative, I want my tracking session to resume automatically when I re-open the app after an unexpected shutdown or reboot, so that I don't have to manually remember to turn it back on.
22. As a Flutter app developer, I want clear, typed exceptions thrown when permissions or location services are disabled, so that my UI can present informative user guidance instead of generic errors.
23. As an enterprise manager, I want midnight distance resets to archive yesterday's total and start today's counter at zero, so that multi-day travel claims are cleanly segregated by date.
24. As an enterprise compliance officer, I want to receive security alert events when location permissions are revoked mid-shift, so that missing tracking gaps can be documented.
25. As a Flutter app developer, I want the plugin to be completely decoupled from any specific host package name, so that I can reuse it across multiple internal enterprise applications without modification.

---

## Implementation Decisions

### Architectural Framework & Governance
* All implementation decisions strictly conform to the 9 established Architecture Decision Records (**ADR-001 through ADR-009**) in `docs/ADR.md`.

### Component Boundaries & Modules
1. **Public Flutter API Facade (`LocationTracker`):** Singleton / static facade providing declarative methods (`startTracking`, `stopTracking`, `isTracking`, `getTodayDistance`, `getDailyHistory`) and reactive streams (`onLocationChanged`, `onStatusChanged`, `onSecurityAlert`).
2. **Platform Interface (`LocationTrackerPlatform`):** Federated contract standardizing method and event signatures.
3. **MethodChannel & EventChannel Bridge:** Native communication channels bound under `'location_tracker'` and `'location_tracker/events'`.
4. **Android Native Service (`DistanceTrackingService`):** Persistent foreground service with `foregroundServiceType="location"` managing `FusedLocationProviderClient`, adaptive polling, and notification rendering.
5. **Anti-Drift Pipeline (`KalmanFilter` & `LocationRepository`):** Coordinates filtered through a 2D Kalman filter and speed threshold ($\ge 0.8\text{ m/s}$ net displacement) before odometer accumulation.
6. **Local Persistence Engine (`AppDatabase` & `DistanceStorage`):** Room SQLite database managing `DailyDistanceEntity` (keyed by `yyyy-MM-dd`) and SharedPreferences storing serialized `TrackingConfig` and recovery flags.
7. **System Integrations:** Dynamic launcher resolution for notifications, `BOOT_COMPLETED` broadcast receiver for reboots, and OEM intent dispatchers for battery exemption navigation.

### Core Data Models (Language Agnostic Contract)
* **`TrackingConfig`:**
  - `notificationTitle: String` (default: `"Workforce Tracking"`)
  - `notificationBodyTemplate: String` (default: `"Distance: {distance} km • {status}"`)
  - `notificationIconResource: String?` (fallback to system default)
  - `notificationChannelId: String` (default: `"location_tracker_channel"`)
  - `updateIntervalMs: int` (default: `8000`)
  - `minDistanceFilterMeters: double` (default: `1.0`)
  - `speedThresholdMps: double` (default: `0.8`)
  - `accuracyFilterMeters: double` (default: `35.0`)
  - `enableAutoStop: bool` (default: `true`)
  - `autoStopHour: int` (default: `0`)
  - `autoStopMinute: int` (default: `0`)
  - `enableNotificationStopButton: bool` (default: `false`)
  - `autoResumeOnBoot: bool` (default: `true`)
* **`LocationPoint`:** `latitude`, `longitude`, `accuracy`, `speed`, `altitude`, `provider`, `timestamp`.
* **`TrackingStatus`:** `STATIONARY`, `MOVING`, `PAUSED`, `PERMISSION_REVOKED`, `RESUMED`.
* **`DailyDistance`:** `date` (`YYYY-MM-DD`), `distanceMeters: double`.
* **`SecurityAlert`:** `alertType` (`MOCK_LOCATION_DETECTED`, `LOCATION_DISABLED`, `PERMISSION_LOST`), `timestamp`, `details`.

### Lifecycle & Concurrency Decisions
* **Race Condition Elimination:** Room writes are coordinated through a single `DistanceStorage` instance utilizing `SupervisorJob() + Dispatchers.IO`. Delta displacement updates do not overwrite cumulative totals.
* **Service Recovery:** Service returns `START_STICKY`. On null restart intent, configuration is reloaded from disk. A crash-loop circuit breaker terminates restarts if $\ge 3$ crashes occur within 60 seconds.
* **Notification Dispatch Throttling:** Status bar updates occur at most once every 3–5 seconds or upon $\ge 10\text{ meters}$ movement to prevent NotificationManager queue throttling.

---

## Testing Decisions

### Testing Philosophy & Seams
We test at the **highest possible architectural seam** to maximize test resilience and verify observable external behavior rather than implementation details:

1. **High Seam 1: Flutter Public API Boundary (Dart Integration & Unit Tests)**
   * Tests exercise `LocationTracker` methods and listen to `onLocationChanged` / `onStatusChanged`.
   * Verifies that config serialization, error mapping, stream emissions, and typed models behave correctly without testing internal method channel plumbing.
2. **High Seam 2: Native Algorithm Boundary (JVM Unit Tests)**
   * Tests feed realistic coordinate datasets (stationary noise, high-speed jumps, walking paths, mock GPS flags) into `KalmanFilter` and `LocationRepository`.
   * Asserts odometer calculation accuracy, noise attenuation, and mock rejection without requiring Android OS device emulators.
3. **High Seam 3: Native Persistence Boundary (Room Database Tests)**
   * Tests run against in-memory Room SQLite database instances (`Room.inMemoryDatabaseBuilder`).
   * Verifies atomic UPSERTs, concurrency under rapid updates, date-based indexing, and 7-day retention pruning.
4. **High Seam 4: System Lifecycle & OS Kill Boundary (Automated ADB Harness)**
   * Automated ADB shell scripts simulate app swipe-away, `am kill`, `dumpsys deviceidle force-idle` (Doze mode), and `BOOT_COMPLETED` broadcasts.
   * Asserts that service state is restored, distance resumes monotonically, and notifications re-appear.

---

## Out of Scope

1. **iOS Implementation:** This specification targets Android (API 26–35+). iOS background location modes (Significant Location Changes, Background Fetch) are reserved for a future iOS-specific extension.
2. **Server-Side Sync Engine:** The plugin provides local persistence and streaming; uploading data to enterprise cloud backends (REST/GraphQL/MQTT) is the responsibility of the host application.
3. **In-App Mapping UI Widgets:** The plugin provides raw coordinate streams; map rendering (Google Maps, Mapbox, Flutter Map) is handled by the host app UI.
4. **Geofencing & Polygon Entry/Exit:** Geofence polygon evaluations and site attendance check-ins belong in a dedicated geofencing module.
5. **Turn-by-Turn Navigation:** Route calculation, turn prompts, and ETA routing are out of scope.

---

## Further Notes

* **Google Play Data Safety:** Host apps must declare location collection in the Play Console Data Safety form under "App Functionality" (Workforce Management / Mileage Reimbursement).
* **Battery Consumption Expectation:** Under continuous active tracking with adaptive throttling, battery consumption is targeted at $< 2.5\% - 3.5\%$ per hour on standard Android hardware with screen off.
* **Permission Strategy:** Host apps are recommended to request permissions in context during employee onboarding with a clear rationale dialog explaining travel allowance computation.
