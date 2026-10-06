# Domain Glossary: Workforce & Mobility Tracking

**Project:** `location_tracker` Flutter Plugin  
**Scope:** Architecture, Mobile OS, Geolocation & Battery Terms

---

| Term | Definition |
| :--- | :--- |
| **FusedLocationProviderClient** | Google Play Services location engine combining GPS, Wi-Fi, and cellular cell towers for optimal accuracy and battery efficiency. |
| **Foreground Service (`location`)** | A long-running Android service with an ongoing user-visible status bar notification, granting the app foreground-level execution priority. |
| **GPS Jitter / Drift** | Random fluctuations in calculated GPS coordinates caused by weak satellite signals, atmospheric interference, and building reflections while stationary. |
| **Kalman Filter** | An algorithm that uses a series of measurements observed over time (containing statistical noise) to estimate unknown variables more accurately than single measurements alone. |
| **Speed Gating** | A filtering threshold where location fixes with an instantaneous speed below a minimum value (e.g., $1.0\text{ m/s}$) are discarded from distance calculation. |
| **Odometer / Cumulative Distance** | The continuous accumulation of valid spatial delta displacements traveled by the employee during their shift. |
| **`START_STICKY`** | Android service flag instructing the OS to recreate the service if killed by the low-memory killer, passing a null intent unless pending intents exist. |
| **Doze Mode & App Standby** | Android OS power management features that defer background CPU, network, and alarms when the device is unplugged, stationary, and screen-off. |
| **`onTaskRemoved`** | Callback invoked on a Service when the user clears the host application from the Android "Recent Apps" switcher. |
| **`EventChannel`** | A Flutter platform channel used for asynchronous stream-based communication from Native to Dart (e.g., streaming location coordinates). |
| **`MethodChannel`** | A Flutter platform channel used for request/response asynchronous messaging between Dart and Native. |
| **Mock Location** | Simulated location coordinates injected via Android Developer Options, often used by employees to fake attendance or travel routes. |
| **Multipath Error** | GPS signal distortion caused by satellite radio waves bouncing off buildings, trees, or terrain before reaching the device receiver. |
| **Adaptive Sampling** | Dynamically throttling GPS polling frequency based on physical activity (e.g., 8s while walking/driving vs. 30–60s when sitting stationary). |
| **Shift Rollover** | Automatic midnight or shift-end reset archiving the previous day's distance and zeroing the active shift odometer. |
