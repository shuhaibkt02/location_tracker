# 12: Observability: Logs, Crash Reporting & Diagnostics

**What to build:** The plugin maintains a thread-safe, bounded circular log buffer of tracking events and diagnostics without leaking plaintext coordinates. The host app can retrieve logs, export diagnostics to file for customer support, and inspect location provider health.

**Blocked by:** 01: Baseline Package & Gradle Sanitization (Prefactor)

**Status:** closed

- [x] `LogHelper` maintains an in-memory ring buffer (up to 1,000 entries) with thread-safe formatting.
- [x] Production logs redact granular GPS coordinates (logs speed, accuracy, delta distance, provider status, but omits raw latitude/longitude for employee privacy and GDPR compliance).
- [x] Dart API `LocationTracker.getLogs()` returns chronological log entries.
- [x] Dart API `LocationTracker.exportLogsToFile()` writes formatted diagnostics to external app storage and returns the file path.
- [x] Dart API `LocationTracker.getServiceDiagnostics()` returns active provider, GPS availability, battery state, and last location fix age.
