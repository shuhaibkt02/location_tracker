import 'package:flutter_test/flutter_test.dart';
import 'package:location_tracker/location_tracker.dart';
import 'package:location_tracker/location_tracker_platform_interface.dart';
import 'package:location_tracker/location_tracker_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockLocationTrackerPlatform
    with MockPlatformInterfaceMixin
    implements LocationTrackerPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final LocationTrackerPlatform initialPlatform = LocationTrackerPlatform.instance;

  test('$MethodChannelLocationTracker is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelLocationTracker>());
  });

  test('getPlatformVersion', () async {
    LocationTracker locationTrackerPlugin = LocationTracker();
    MockLocationTrackerPlatform fakePlatform = MockLocationTrackerPlatform();
    LocationTrackerPlatform.instance = fakePlatform;

    expect(await locationTrackerPlugin.getPlatformVersion(), '42');
  });
}
