# 04: EventChannel Streaming & Hot-Restart Safety

**What to build:** While tracking is active, location coordinates stream continuously into Flutter via an `EventChannel`. Triggering Flutter Hot Reload, Hot Restart, or navigating away and back does not crash native code or leak stream listeners.

**Blocked by:** 03: FGS Lifecycle, Tap-to-Open, and Config Persistence

**Status:** done

- [x] Dart API exposes `LocationTracker.onLocationChanged` emitting a strongly-typed `Stream<LocationPoint>`.
- [x] Native Kotlin implements `EventChannel.StreamHandler` for `'location_tracker/events'`.
- [x] Native service broadcasts location results from `FusedLocationProviderClient` to active event sinks safely on the main looper.
- [x] Hot Restart in Flutter cleanly cancels and re-registers the event stream without throwing `IllegalStateException`.
- [x] Unsubscribing from the Dart stream does not stop the background service or location collection.
- [x] Data model `LocationPoint` contains latitude, longitude, accuracy, speed, altitude, provider, and ISO timestamp.
