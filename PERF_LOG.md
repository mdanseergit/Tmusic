# TMusic Performance Log (`PERF_LOG.md`)

**Date:** 2026-11-24  
**Branch:** `v2/perf-lowend`  
**Application ID (Benchmark):** `com.metrolist.music.benchmark`  
**Application ID (Release):** `com.metrolist.music` (Unchanged)  
**Reference Low-End Device:** Android 16 (API 36) Emulator `tmusic_phase1` (2 GB RAM, 4 vCPU, x86_64)  
**Build Profile:** `fossBenchmark` (Minified, R8 shrink enabled, non-debuggable, signed with debug key)

---

## 1. APK Size Comparison

| Variant | Size (Bytes) | Size (MB) | Comparison vs v1 |
|:---|:---:|:---:|:---|
| **v1 Shipped APK (`TMUSIC.apk`)** | 52,598,885 | 50.16 MB | Baseline reference |
| **`fossDebug` (Local)** | 55,793,640 | 53.21 MB | +6.1% |
| **`fossBenchmark` (`.benchmark`)** | 28,594,402 | **27.27 MB** | **-45.6% (-22.89 MB)** |

---

## 2. Phase A Baseline Numbers (Measured on Reference 2GB Target)

| Metric | Target (Low-End 2GB) | Phase A Baseline (Measured on AVD `tmusic_phase1`) | Status | Notes |
|:---|:---:|:---:|:---:|:---|
| **APK Size** | < 30 MB | **27.27 MB** | ✅ Pass | -45.6% vs v1 APK |
| **Cold Start (TotalTime to First Frame)** | < 3.0 s | **6.74 s (6,743 ms)** | ❌ Severe Lag | 2.2× over budget; un-profiled startup + eager inits |
| **Home Scroll Jank Rate (20s)** | < 10.0% | **89.24% (398 / 446 frames)** | ❌ Catastrophic | 366 slow UI thread frames, 390 slow draw commands |
| **Frame Time (p50 / Median)** | < 16 ms | **85 ms** | ❌ Severe Lag | Dropping frames constantly (vsync missed: 338) |
| **Frame Time (p90)** | < 25 ms | **150 ms** | ❌ Severe Lag | 6× over 16.6ms frame budget |
| **Frame Time (p99)** | < 50 ms | **250 ms** | ❌ Severe Lag | Quarter-second hitches during fling |
| **Slow Bitmap Uploads** | 0 | **114 frames** | ❌ Warning | Unconstrained Coil decodes & hardware bitmaps |
| **Java Heap (Active Browsing)** | < 80 MB | **47.15 MB** (47,152 KB) | ✅ Within limit | Room + Compose allocations |
| **Native Heap** | < 100 MB | **50.10 MB** (50,100 KB) | ✅ Within limit | Gralloc + Skia surfaces |
| **Total PSS Memory** | < 180 MB | **179.98 MB** (179,979 KB) | ⚠️ At Limit | Right at 180 MB budget on 2GB RAM device |

---

## 3. Real Runtime Confirmation of Top 3 Findings

1. **Recomposition Storms & Slow UI Thread:**
   * **Measured:** `Number Slow UI thread: 366` out of 446 frames (82% of frames stalled on the Main UI thread).
   * **Cause:** Root-level reading of `isPlaying` and `mediaMetadata` in `HomeScreen.kt:660`, combined with 40+ concurrent Room Flow subscriptions inside `QuickPicks` and `ForgottenFavorites` rows.
2. **Slow Issue Draw Commands & Bitmap Uploads:**
   * **Measured:** `Number Slow issue draw commands: 390`, `Number Slow bitmap uploads: 114`.
   * **Cause:** Unconditional 60–150 dp GPU blurs in `MiniPlayer` and `Player` (`MiniPlayer.kt:407`, `Player.kt:886`) combined with global Coil `crossfade(true)` blending on every recycled thumbnail.
3. **Never-Ending Work (Widget Loop & Leaky Collectors):**
   * **Measured:** Persistent `MusicService` FGS bindings and IPC overhead running every 200 ms during playback.
