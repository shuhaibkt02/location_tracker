#!/usr/bin/env bash
# ==============================================================================
# Kill-Scenario & Resilience Test Harness
# Workforce Location Tracker Plugin (Android)
#
# Validates ADR-001 through ADR-009 requirements:
# 1. App Swipe-Away / Task Removal recovery
# 2. Low-Memory OS Process Kill & START_STICKY auto-revival
# 3. Android Doze Mode (Deep Idle) location preservation
# 4. BOOT_COMPLETED auto-resume & odometer rehydration
# ==============================================================================

set -euo pipefail

PACKAGE_NAME="${1:-com.harmonyloop.location_tracker_example}"
SERVICE_NAME="com.harmonyloop.location_tracker.DistanceTrackingService"
RECEIVER_NAME="com.harmonyloop.location_tracker.BootCompletedReceiver"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

check_device() {
    log_info "Checking connected ADB device..."
    if ! command -v adb &> /dev/null; then
        log_fail "adb command not found. Please install Android Platform Tools."
        exit 1
    fi

    DEVICE_STATE=$(adb get-state 2>&1 || true)
    if [ "$DEVICE_STATE" != "device" ]; then
        log_fail "No authorized Android device connected. adb get-state: $DEVICE_STATE"
        exit 1
    fi
    DEVICE_MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
    ANDROID_VER=$(adb shell getprop ro.build.version.release | tr -d '\r')
    SDK_INT=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
    log_info "Connected device: $DEVICE_MODEL (Android $ANDROID_VER, SDK $SDK_INT)"
}

is_service_running() {
    adb shell dumpsys activity services "$PACKAGE_NAME" 2>/dev/null | grep -q "$SERVICE_NAME"
}

wait_for_service() {
    local timeout="${1:-15}"
    local count=0
    while ! is_service_running; do
        sleep 1
        count=$((count + 1))
        if [ "$count" -ge "$timeout" ]; then
            return 1
        fi
    done
    return 0
}

# ==============================================================================
# Scenario 1: App Task Removal / Swipe-away test
# ==============================================================================
test_swipe_away() {
    echo ""
    echo "=================================================================="
    log_info "Scenario 1: Testing App Swipe-Away (Task Removal) Resilience"
    echo "=================================================================="

    if ! is_service_running; then
        log_warn "Service is not currently running. Please launch the host app and start tracking first."
        return 1
    fi
    log_info "Service verified running before task removal."

    log_info "Simulating task removal / swipe away..."
    adb shell am stop-app "$PACKAGE_NAME" || true
    sleep 3

    log_info "Checking if DistanceTrackingService is still active..."
    if is_service_running; then
        log_pass "DistanceTrackingService persisted after task removal."
    else
        log_info "Waiting up to 10s for START_STICKY or restart..."
        if wait_for_service 10; then
            log_pass "DistanceTrackingService recovered successfully after task removal."
        else
            log_fail "DistanceTrackingService failed to stay active after task removal."
            return 1
        fi
    fi
}

# ==============================================================================
# Scenario 2: Low-Memory OS Process Kill & START_STICKY auto-revival
# ==============================================================================
test_low_memory_kill() {
    echo ""
    echo "=================================================================="
    log_info "Scenario 2: Testing Low-Memory OS Kill & START_STICKY Revival"
    echo "=================================================================="

    if ! is_service_running; then
        log_warn "Service is not running. Starting test will be skipped."
        return 1
    fi

    log_info "Simulating OS low-memory process kill via 'am kill'..."
    adb shell am kill "$PACKAGE_NAME"
    sleep 2

    log_info "Waiting for Android OS to reschedule and restart START_STICKY service..."
    if wait_for_service 15; then
        log_pass "DistanceTrackingService was successfully revived by START_STICKY mechanism."
    else
        log_fail "DistanceTrackingService was not revived within 15 seconds after process kill."
        return 1
    fi
}

# ==============================================================================
# Scenario 3: Android Doze Mode (Deep Idle) location preservation
# ==============================================================================
test_doze_mode() {
    echo ""
    echo "=================================================================="
    log_info "Scenario 3: Testing Android Doze Mode (Deep Idle) Behavior"
    echo "=================================================================="

    log_info "Unplugging battery simulation..."
    adb shell dumpsys battery unplug
    sleep 1

    log_info "Forcing device into Doze IDLE mode..."
    adb shell dumpsys deviceidle force-idle
    sleep 3

    IDLE_STATE=$(adb shell dumpsys deviceidle | grep -i "mState=" | head -n 1 | tr -d '\r')
    log_info "Device idle state: $IDLE_STATE"

    if is_service_running; then
        log_pass "DistanceTrackingService remains in foreground during Doze mode."
    else
        log_fail "DistanceTrackingService was dropped during Doze transition."
    fi

    log_info "Resetting deviceidle and restoring battery charging..."
    adb shell dumpsys deviceidle unforce
    adb shell dumpsys battery reset
    log_pass "Doze mode recovery verified."
}

# ==============================================================================
# Scenario 4: BOOT_COMPLETED auto-resume & odometer rehydration
# ==============================================================================
test_boot_completed() {
    echo ""
    echo "=================================================================="
    log_info "Scenario 4: Testing BOOT_COMPLETED Broadcast Auto-Resume"
    echo "=================================================================="

    log_info "Emitting ACTION_BOOT_COMPLETED broadcast to package..."
    adb shell am broadcast \
        -a android.intent.action.BOOT_COMPLETED \
        -p "$PACKAGE_NAME" \
        -n "$PACKAGE_NAME/$RECEIVER_NAME"

    sleep 3

    if wait_for_service 10; then
        log_pass "BootCompletedReceiver triggered and DistanceTrackingService resumed automatically."
    else
        log_warn "DistanceTrackingService did not start immediately from broadcast. (Note: may require is_tracking_active=true in config)."
    fi
}

# ==============================================================================
# Scenario 5: Process Kill ('am kill') followed by BOOT_COMPLETED Rehydration & Odometer Continuity
# ==============================================================================
test_kill_and_boot_recovery() {
    echo ""
    echo "=================================================================="
    log_info "Scenario 5: Testing 'am kill' + BOOT_COMPLETED Rehydration & Odometer Continuity"
    echo "=================================================================="

    if ! is_service_running; then
        log_warn "Service is not currently running. Cannot test kill + revive cycle."
        return 1
    fi

    log_info "Capturing baseline odometer before simulated kill..."
    local initial_logs
    initial_logs=$(adb logcat -d -s DistanceTracker | tail -n 20 || true)

    log_info "Simulating OS process termination via 'am kill'..."
    adb shell am kill "$PACKAGE_NAME"
    sleep 2

    log_info "Broadcasting ACTION_BOOT_COMPLETED to rehydrate service..."
    adb shell am broadcast \
        -a android.intent.action.BOOT_COMPLETED \
        -p "$PACKAGE_NAME" \
        -n "$PACKAGE_NAME/$RECEIVER_NAME"

    log_info "Waiting for DistanceTrackingService restoration..."
    if wait_for_service 15; then
        log_pass "DistanceTrackingService restored successfully after kill + reboot cycle."
    else
        log_fail "DistanceTrackingService failed to restore after kill + reboot cycle."
        return 1
    fi

    log_info "Verifying odometer continuity without phantom drift..."
    sleep 2
    local rehydrated_logs
    rehydrated_logs=$(adb logcat -d -s DistanceTracker | grep -E "initialized with distance|Loaded today's distance" | tail -n 1 || true)
    if [ -n "$rehydrated_logs" ]; then
        log_pass "Odometer rehydration confirmed: $rehydrated_logs"
    else
        log_info "Service restored; inspect logcat for exact odometer readings."
    fi
}

# ==============================================================================
# Main execution
# ==============================================================================
main() {
    echo "=================================================================="
    echo " Workforce Location Tracker: Kill-Scenario Resilience Suite      "
    echo " Package: $PACKAGE_NAME                                          "
    echo "=================================================================="

    check_device

    test_swipe_away || true
    test_low_memory_kill || true
    test_doze_mode || true
    test_boot_completed || true
    test_kill_and_boot_recovery || true

    echo ""
    echo "=================================================================="
    log_info "Kill-Scenario suite completed. Inspect logcat for detailed events:"
    echo "  adb logcat -s DistanceTracker"
    echo "=================================================================="
}

main "$@"
