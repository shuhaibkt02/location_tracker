# 11: OEM Battery Helpers & Guidance UI

**What to build:** The host app can check if battery optimizations are active, prompt the user to whitelist the app from battery restrictions, and navigate sales reps directly to OEM-specific autostart settings on aggressive devices (Xiaomi, Samsung, Oppo, Vivo, Huawei).

**Blocked by:** 01: Baseline Package & Gradle Sanitization (Prefactor)

**Status:** closed

- [x] Dart API `LocationTracker.isIgnoringBatteryOptimizations()` returns boolean battery optimization status via `PowerManager.isIgnoringBatteryOptimizations()`.
- [x] Dart API `LocationTracker.requestIgnoreBatteryOptimizations()` launches system dialog via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- [x] Dart API `LocationTracker.openOemBatterySettings()` launches manufacturer-specific autostart or background managers on Xiaomi (MIUI/HyperOS), Samsung (One UI), Oppo (ColorOS), Vivo (Funtouch), and Huawei (EMUI).
- [x] Dart API returns `bool` indicating whether the OEM settings intent successfully resolved and opened.
- [x] Falls back cleanly to general Android App Details settings if OEM-specific intent fails.
