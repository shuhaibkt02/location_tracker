
import 'location_tracker_platform_interface.dart';

class LocationTracker {
  Future<String?> getPlatformVersion() {
    return LocationTrackerPlatform.instance.getPlatformVersion();
  }
}
