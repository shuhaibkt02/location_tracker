# 19: Typed Exception Hierarchy & Call-Site Refactor

**What to build:** The Dart client library exposes specialized, strongly-typed exceptions allowing host apps to distinguish between permission denial, disabled system location, and general failures without string-matching error codes. Duplicated `PlatformException` error-handling boilerplate across 15 method channel invocations is refactored into a reusable execution wrapper.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Specialized exceptions extending `LocationTrackerException` are declared in `exceptions.dart`:
  - `LocationPermissionDeniedException`: thrown when required permissions are missing or denied.
  - `LocationServicesDisabledException`: thrown when system GPS/location is disabled.
  - `LocationTrackingNotActiveException`: thrown when querying active session data while stopped.
- [ ] Boilerplate `try/catch` wrapping duplicated across 15+ method channel invocations in `location_tracker_method_channel.dart` is refactored into a centralized `_invoke<T>()` helper mapping platform error codes to typed exceptions.
- [ ] Dart unit tests verify that platform errors cleanly throw their corresponding typed exception subclasses.
