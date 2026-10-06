# Device Verification Runbook & Field Testing Matrix

## 1. Overview
This runbook provides the standard operating procedures for validating the `location_tracker` plugin across physical Android devices and OEM skins, specifically focusing on background resilience, anti-drift odometer accuracy, and anti-tamper security.

---

## 2. OEM Device Test Matrix

| OEM / ROM | Tested Devices | Key Settings to Verify | Expected Outcome |
| :--- | :--- | :--- | :--- |
| **Google Pixel / AOSP** | Pixel 7, 8 (Android 14/15) | Battery Usage -> "Unrestricted" | Continuous tracking through Doze, FGS stays pinned in notification shade |
| **Samsung One UI** | Galaxy S23, A54 (One UI 6.x) | Settings -> Apps -> Battery -> "Unrestricted" + Background usage limits -> "Never auto sleeping apps" | Survives App Standby bucket promotion; FGS recovers if app swiped away |
| **Xiaomi (MIUI / HyperOS)** | Redmi Note 12, Xiaomi 13 | Security app -> Autostart -> Enable; Battery Saver -> "No restrictions" | Service starts on boot via `BootCompletedReceiver`; does not get terminated after 15 min lock screen |
| **Oppo / Realme (ColorOS)** | Reno 10, Realme GT | App Info -> Battery Usage -> "Allow background activity" + "Allow auto-launch" | Background location updates persist without throttle |
| **Vivo (Funtouch / OriginOS)** | Vivo V29, iQOO Neo | App Manager -> Autostart -> Enable; Battery Management -> "High background power consumption" | Service maintains active WakeLock and GPS acquisition |

---

## 3. Automated ADB Kill Scenario Execution

Connect a target Android test device with USB debugging enabled, ensure the host application is installed and active, then execute:

```bash
# Run automated kill-scenario suite
./scripts/test_kill_scenarios.sh <your.package.name>
```

### Verification Points:
1. **Swipe Away (Task Removed):**
   - Notification must NOT be dismissed.
   - `START_STICKY` service remains active or restarts within 3 seconds.
2. **Process Kill (`am kill`):**
   - Notification temporarily refreshes.
   - Circuit breaker in `CrashProtector.kt` confirms restart is legitimate and restores odometer distance from Room DB.
3. **Doze Mode (`dumpsys deviceidle force-idle`):**
   - WakeLock maintains CPU execution during location callbacks.
   - Status transitions from `MOVING` to `STATIONARY` / `PAUSED` without crashing.
4. **Reboot (`BOOT_COMPLETED`):**
   - On device restart, `BootCompletedReceiver` triggers.
   - Notification appears in status bar with today's persisted cumulative distance.

---

## 4. Field Testing Protocol (Accuracy & Anti-Drift)

### Test A: Stationary Office Jitter (30 Minutes)
* **Goal:** Verify that stationary desk or indoor drift does NOT accumulate false mileage.
* **Procedure:**
  1. Start tracking inside an office or apartment with suboptimal GPS coverage (accuracy $15\text{ m} - 40\text{ m}$).
  2. Leave the device completely motionless on a table for 30 minutes.
  3. Keep the screen turned off.
* **Pass Criteria:**
  - Accumulated distance change: $\le 10\text{ meters}$ across 30 minutes.
  - Tracking status reflects `STATIONARY` (and transitions to `PAUSED` after 3 minutes).
  - Status bar notification text displays `0.00 km • Stationary` (or `Paused`).

### Test B: Urban Canyon Walking Route (1.0 km Benchmark)
* **Goal:** Verify Kalman filter smoothing and speed-gating response on real pedestrian movement.
* **Procedure:**
  1. Walk a known, pre-measured $1.0\text{ km}$ outdoor route (with tall buildings / trees).
  2. Normal walking speed: $1.1 - 1.4\text{ m/s}$ ($> 0.8\text{ m/s}$ threshold).
  3. Pause at pedestrian traffic lights for 60 seconds (speed drops below $0.8\text{ m/s}$).
* **Pass Criteria:**
  - Total odometer reading within $\pm 3\%$ of calibrated benchmark distance ($0.97\text{ km} - 1.03\text{ km}$).
  - Stationary stops at traffic lights do not accumulate spurious drift.

### Test C: Mock GPS Injection (ADR-008 Security Test)
* **Goal:** Confirm that spoofed coordinates are dropped and security alerts fire.
* **Procedure:**
  1. Enable Developer Options -> "Select mock location app" -> select a Fake GPS app.
  2. Teleport location to coordinates 50 km away.
* **Pass Criteria:**
  - Total distance odometer does NOT increase.
  - Stream `LocationTracker.onSecurityAlert` receives `MOCK_LOCATION_DETECTED`.
  - Service log records rejected fix with provider and timestamp.
