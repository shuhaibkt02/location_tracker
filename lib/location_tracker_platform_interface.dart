import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'location_tracker_method_channel.dart';

abstract class LocationTrackerPlatform extends PlatformInterface {
  /// Constructs a LocationTrackerPlatform.
  LocationTrackerPlatform() : super(token: _token);

  static final Object _token = Object();

  static LocationTrackerPlatform _instance = MethodChannelLocationTracker();

  /// The default instance of [LocationTrackerPlatform] to use.
  ///
  /// Defaults to [MethodChannelLocationTracker].
  static LocationTrackerPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [LocationTrackerPlatform] when
  /// they register themselves.
  static set instance(LocationTrackerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
