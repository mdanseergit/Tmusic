# TMusic Performance Log (`PERF_LOG.md`)

**Date:** 2026-11-24  
**Branch:** `v2/perf-lowend`  
**Reference Low-End Spec:** 2 GB RAM, 4 cores, Android 8–10 class (AVD `tmusic_phase1`: Android 36, 2GB RAM, x86_64)  
**High-End Reference Spec:** Connected Android phone via USB debugging  
**Build Profile:** `fossBenchmark` (Minified, R8 shrink enabled, non-debuggable, signed with debug key)

---

## 1. APK Size Baseline

| Build Variant | Size (Bytes) | Size (MB) | Notes |
|:---|:---:|:---:|:---|
| **`fossDebug`** | 55,793,640 | 53.21 MB | Unminified, debug symbols, debug instrumentation |
| **`fossBenchmark`** | 28,594,154 | 27.27 MB | **-48.7% smaller** (Minified, R8 full mode, resource shrunk, stripped quickjs) |

---

## 2. Runtime Performance Baselines (Phase A)

*Note: Measurements require an active ADB connection. At checkpoint 1, no device was attached (`adb devices` returned 0 devices). The table below documents the targets, measured APK size, and exact protocol to record real runtime numbers.*

| Metric | Target (High-End) | Target (Low-End 2GB) | Baseline (High-End Phone) | Baseline (Low-End AVD `tmusic_phase1`) | Status |
|:---|:---:|:---:|:---:|:---:|:---:|
| **APK Size** | < 35 MB | < 30 MB | **27.27 MB** | **27.27 MB** | ✅ Within budget |
| **Cold Start (Time to First Frame)** | < 2.0 s | < 3.0 s | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Home Scroll Jank Rate (20s)** | < 5.0% | < 10.0% | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Frame Time (p90 / p99)** | < 16 ms / < 32 ms | < 25 ms / < 50 ms | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Song Tap → UI Reaction** | < 100 ms | < 150 ms | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Song Tap → Audio Start (Cached)** | < 1.0 s | < 1.5 s | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Java Heap (after 5m use)** | < 120 MB | < 80 MB | *Awaiting phone connection* | *Awaiting run* | Pending test run |
| **Total PSS Memory (after 5m use)** | < 250 MB | < 180 MB | *Awaiting phone connection* | *Awaiting run* | Pending test run |

---

## 3. How to Run Real Measurements

### A. Install Benchmark APK
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\Projects\TMUSIC APP + WEB\Tmusic-App\app\build\outputs\apk\foss\benchmark\app-foss-benchmark.apk"
```

### B. Cold Start Timing
```powershell
# Force-stop the app
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.metrolist.music

# Measure cold startup time (TotalTime is the key metric)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -W -n com.metrolist.music/.MainActivity
```

### C. Home Screen Scroll Jank (20-second test)
```powershell
# Reset frame statistics
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dumpsys gfxinfo com.metrolist.music reset

# (Scroll Home screen up and down for 20 seconds, then collect stats)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dumpsys gfxinfo com.metrolist.music framestats
```

### D. Memory Profile (Heap & PSS)
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dumpsys meminfo com.metrolist.music
```

### E. Low-Memory Cache Trim Test
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am send-trim-memory com.metrolist.music RUNNING_CRITICAL
```
