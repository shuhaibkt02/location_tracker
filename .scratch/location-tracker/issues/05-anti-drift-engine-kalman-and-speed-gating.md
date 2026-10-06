# 05: Anti-Drift Engine: Kalman Filter & Speed Gating

**What to build:** The location pipeline filters out stationary GPS jitter and multipath noise so sales reps sitting in offices/meetings do not accumulate phantom mileage. The odometer calculates true physical displacement based on filtered coordinates.

**Blocked by:** 04: EventChannel Streaming & Hot-Restart Safety

**Status:** ready-for-agent

- [ ] GPS updates with horizontal accuracy worse than configured threshold (default $35\text{m}$) are discarded.
- [ ] Speed gating discards points where calculated speed is below threshold (default $0.8\text{ m/s}$) unless cumulative net displacement exceeds $15\text{ meters}$.
- [ ] Raw GPS points are smoothed through an enhanced 2D Kalman filter computing latitude and longitude covariance updates.
- [ ] Outlier jumps ($> 1\text{ km}$ or $> 200\text{ km/h}$) are rejected.
- [ ] Unit tests verify that stationary noise sequences ($10\text{m}$ random jitter over 100 points) produce $< 20\text{ meters}$ total accumulated displacement.
- [ ] Unit tests verify that walking trajectories ($1.2\text{ m/s}$ linear motion) accumulate distance within $5\%$ of ground truth.
