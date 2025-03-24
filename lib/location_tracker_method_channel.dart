import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'location_tracker_platform_interface.dart';

/// An implementation of [LocationTrackerPlatform] that uses method channels.
class MethodChannelLocationTracker extends LocationTrackerPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('location_tracker');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
