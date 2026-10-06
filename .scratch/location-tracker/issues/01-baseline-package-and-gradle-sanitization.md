# 01: Baseline Package & Gradle Sanitization (Prefactor)

**What to build:** The plugin builds cleanly as a Flutter Android library with consistent namespacing. The host app can include the plugin, invoke `getPlatformVersion()`, and receive a valid version response without runtime `ClassNotFoundException` or machine-specific build errors.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] All Kotlin packages, Gradle group, and manifest package definitions are standardized to `com.harmonyloop.location_tracker`.
- [x] Kotlin plugin entrypoint class is renamed to `LocationTrackerPlugin` matching the declaration in `pubspec.yaml`.
- [x] MethodChannel identifier is aligned across Dart and Kotlin to `'location_tracker'`.
- [x] Hardcoded local machine path (`${System.env.HOME}/Tools/flutter_linux...`) in `android/build.gradle` is removed.
- [x] Unused dependencies (`androidx.activity:activity-compose`, `com.google.android.gms:play-services-maps`) are removed from Gradle.
- [x] Unit test `LocationTrackerPluginTest` instantiates `LocationTrackerPlugin` and passes `getPlatformVersion` verification.
- [x] Plugin compiles without warnings or unresolved import errors.
