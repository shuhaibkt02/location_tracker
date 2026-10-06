# 02: Permissions, Rationale UI, and Denial/Revocation Handling

**What to build:** The host app can check and request all necessary permissions to track workforce locations, showing appropriate rationale prompts and responding gracefully if permissions are denied or revoked mid-tracking.

**Blocked by:** 01: Baseline Package & Gradle Sanitization (Prefactor)

**Status:** ready-for-agent

- [ ] Plugin provides Dart API `LocationTracker.checkPermissions()` returning a structured `PermissionStatus` object.
- [ ] Plugin provides Dart API `LocationTracker.requestPermissions()` requesting `ACCESS_FINE_LOCATION` and `POST_NOTIFICATIONS` (on Android 13+).
- [ ] Manifest explicitly declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION` without declaring `ACCESS_BACKGROUND_LOCATION` (ADR-001).
- [ ] If permission is revoked while tracking is active, the native service pauses location polling, transitions tracking status to `PERMISSION_REVOKED`, and emits an alert to Flutter.
- [ ] If notification permission is denied on Android 13+, the plugin returns a descriptive error state before attempting to launch the foreground service.
