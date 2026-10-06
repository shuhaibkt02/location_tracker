# 21: Mock-Location Anti-Tamper Hardening (ADR-008 Compliance)

**What to build:** Mock GPS location injection is strictly guarded against release builds to prevent travel claim and mileage fraud. Any developer bypass is restricted exclusively to debug environments (`BuildConfig.DEBUG`), preventing production tampering even if configuration parameters are manipulated.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] In `LocationRepository.kt`, mock location acceptance requires BOTH `BuildConfig.DEBUG` and `config.allowMockLocationsInDebug == true`. Release builds unconditionally reject mock locations regardless of configuration flags.
- [ ] If a mock location is detected in a release build, the fix is immediately discarded, odometer accumulation is rejected, and a `MOCK_LOCATION_DETECTED` security alert is dispatched.
- [ ] ADR-008 documentation in `docs/ADR.md` is updated to formally specify the debug-only carve-out for emulator testing and QA automation.
- [ ] JVM unit tests verify that mock location fixes are rejected when `BuildConfig.DEBUG` is false, and allowed only when explicitly enabled in debug mode.
