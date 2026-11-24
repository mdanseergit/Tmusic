# TMusic Home Screen & UI Lag Analysis (Phase A Diagnosis)

**Date:** 2026-11-24  
**Branch:** `v2/perf-lowend`  
**Target:** Android 8.0+ (API 26–36), 1.5–2GB RAM Low-End Devices & Flagship Devices  
**Scope:** Root-cause investigation of software-driven UI lag on the Home screen, song lists, and song selection path.

---

## Executive Summary

The reported Home screen lag (present even on high-end phones) is caused by **excessive composition invalidations, per-item database subscriptions, unthrottled background polling, and heavy drawing effects**, rather than hardware limitations.

Specifically:
1. **Recomposition Storms:** Root-level subscription to player state (`isEffectivelyPlaying`, `mediaMetadata`) forces the entire `HomeScreen` and its nested carousels/grids to recompose on every song change or play/pause state change. In addition, individual rows in `QuickPicks` and `ForgottenFavorites` each subscribe directly to `database.song()`, creating up to 40+ concurrent Flow collectors on the Home screen alone.
2. **Work That Never Stops:** A background polling loop in `MusicService` rebuilds and emits `RemoteViews` for home-screen widgets **every 200 ms** whenever audio is playing—even when no widget is installed on the user's launcher.
3. **Database Scans Without Indexes:** The `Event` (play history) table has no index on `timestamp` and is never pruned. Every Home visit runs multiple aggregations (`mostPlayedSongs`, `mostPlayedAlbums`, `mostPlayedArtists`, `latestEvent`) requiring sequential table scans.
4. **Drawing & Image Restraint:** Unconditional 60–150 dp blurs in `MiniPlayer` and `Player`, global Coil `crossfade(true)`, and large drop shadows add significant compositing overhead.
5. **No Optimistic Tap Feedback:** Song taps wait on network and queue resolution before providing visible UI state updates.

---

## Ranked Diagnosis of the 12 Suspects

| Rank | Suspect | Status | Measured / Architectural Cost | Primary File & Line Evidence |
|:----:|:---|:---:|:---|:---|
| **1** | **Recomposition Storms** | **APPLIES** | **Critical** (Full-screen invalidations on song changes; 40+ individual Room query flows inside lazy items) | `HomeScreen.kt:660-661, 851, 1567-1570, 1798-1800, 2120-2122` |
| **2** | **Work That Never Stops** | **APPLIES** | **Very High** (200 ms widget update loop; leaky `PlayerConnection` collectors; continuous partial wakelock) | `MusicService.kt:4623-4628`, `PlayerConnection.kt:670-678`, `ListenTogetherClient.kt:823` |
| **3** | **Database Churn & Missing Indexes** | **APPLIES** | **Critical (Long-Term)** (Unbounded `Event` growth, no `timestamp` index, full-table scans on every Home load) | `Event.kt:27-32`, `DatabaseDao.kt:1473-1533`, `MusicService.kt:3968-3990` |
| **4** | **Home Data Pipeline** | **APPLIES** | **High** (Uncoordinated burst of 15+ network/DB queries; no persistent offline cache for Home sections) | `HomeViewModel.kt:441-500, 274-320, 366-439` |
| **5** | **List Structure & Missing Keys/Types** | **APPLIES** | **High** (Missing keys on community items; zero `contentType` across all lazy lists; heavy nested grids) | `HomeScreen.kt:1879`, `HomeScreen.kt:1188-2300` |
| **6** | **Song-Tap Path** | **APPLIES** | **High** (No optimistic highlight; network queue resolution blocks immediate audio start) | `HomeScreen.kt:826-836`, `MusicService.kt:1774-1814` |
| **7** | **Expensive Drawing** | **APPLIES** | **High** (Unconditional 60–150 dp blurs in MiniPlayer & Player bypassing `blurIfHighEnd`; 16–24 dp shadows) | `MiniPlayer.kt:407`, `Player.kt:886`, `Effects.kt:27-29` |
| **8** | **Images (Coil Setup)** | **APPLIES** | **Medium–High** (Global crossfade enabled on all thumbnails; `runBlocking` fallback in `newImageLoader`) | `App.kt:288-295` |
| **9** | **Build Configuration & Baseline Profile** | **APPLIES** | **Medium–High** (`baseline-prof.txt` present but `androidx.profileinstaller` missing from dependencies) | `app/build.gradle.kts:116-144`, `gradle/libs.versions.toml` |
| **10** | **Memory & GC Allocations** | **APPLIES** | **Medium** (Repeated in-composition lambdas, `.filter`, `.shuffled()`, and temporary objects during scroll) | `HomeScreen.kt:812-910, 1066-1137` |
| **11** | **Startup Work** | **APPLIES** | **Medium** (Eager `ListenTogetherManager.initialize()` and background polling loop at startup) | `MainActivity.kt:371`, `App.kt:105-113` |
| **12** | **Debug Code in Release** | **DOES NOT APPLY** | **Clean** (Timber guarded with `BuildConfig.DEBUG`; `println` and `console.log` removed in commit `885f06c`) | `App.kt:89-91`, `AutoPlaylistScreen.kt` |

---

## Detailed Evidence & Code Analysis

### 1. Recomposition Storms (Critical)
* **Root state reading:** In `HomeScreen.kt:660-661`:
  ```kotlin
  val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
  val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
  ```
  Because these state reads occur at the top of the `HomeScreen` composable, every song transition, seek, or pause/play toggle invalidates `HomeScreen` from the root down.
* **Per-item state passing:** Lines 851–852, 859–860, 915–916 pass `isActive = it.id == mediaMetadata?.id` and `isPlaying = isPlaying` down to every child item, forcing recomposition of every visible row instead of only the affected items.
* **In-item database queries:**
  - `HomeScreen.kt:1567-1570`: Each item in the Speed Dial pager executes:
    `val isPinned by database.speedDialDao.isPinned(item.id).collectAsStateWithLifecycle(initialValue = false)`
  - `HomeScreen.kt:1798-1800` (Quick Picks):
    `val song by database.song(originalSong.id).collectAsStateWithLifecycle(initialValue = originalSong)`
  - `HomeScreen.kt:2120-2122` (Forgotten Favorites):
    `val song by database.song(originalSong.id).collectAsStateWithLifecycle(initialValue = originalSong)`
  - `HomeScreen.kt:228` (Community Playlists):
    `val dbPlaylist by database.playlistByBrowseId(item.playlist.id).collectAsStateWithLifecycle(initialValue = null)`
  *Impact:* Scrolling through Home instantiates dozens of concurrent Room flow observers that independently recompose individual tiles.
* **Unstable lambdas:** `localGridItem` (lines 812–910) and `ytGridItem` (lines 912–1024) are recreated on every pass and capture local scope, preventing the Compose compiler from skipping items.

### 2. Work That Never Stops (Very High)
* **200 ms Widget Refresh Loop:** In `MusicService.kt:4623-4628`:
  ```kotlin
  while (isActive) {
      if (player.isPlaying) {
          updateWidgetUI(true)
      }
      delay(200)
  }
  ```
  Every 200 ms during playback, `updateWidgetUI` builds and pushes `RemoteViews` for all widget layouts across IPC, regardless of whether a widget is active.
* **PlayerConnection Collector Leak:** In `PlayerConnection.kt:670-678`, `dispose()` only detaches `attachedPlayer?.removeListener(this)`. Coroutine scopes and hot state flows (`stateIn`) created during initialization are never cancelled, leaking collectors on every service reconnection.
* **Wakelock Churn:** `ListenTogetherClient.kt:823` continuously acquires and refreshes a partial `WakeLock` in an active loop.
* **Slider Progress Frequency:** `Player.kt:763` polls `delay(100)` (10 Hz) for progress slider updates.

### 3. Database Churn & Missing Indexes (Critical Long-Term)
* **Missing Index on Event.timestamp:** In `db/entities/Event.kt:27-32`, only `songId` is indexed:
  ```kotlin
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(index = true) val songId: String,
  val timestamp: LocalDateTime,
  val playTime: Long,
  ```
  `DatabaseDao` queries (`mostPlayedSongs`, `mostPlayedAlbums`, `mostPlayedArtists`, `latestEvent`) all filter by `timestamp >= :fromTimeStamp`. Without an index on `timestamp`, SQLite executes a full table scan across all history rows on every Home load.
* **No Pruning:** `Event` rows accumulate indefinitely; no background task or startup routine bounds the table size.

### 4. Home Data Pipeline (High)
* **Uncoordinated Bursts:** In `HomeViewModel.kt:441-500`, `load()` fires 6 distinct database/network queries concurrently, followed immediately by Phase 2 background tasks (`getDailyDiscover` and `getCommunityPlaylists`) firing 20+ additional HTTP calls.
* **No Offline-First Caching:** Home state is not persisted in Room. On cold start, sections remain empty/shimmering until the network resolves, creating layout shifts and jank.

### 5. List Structure & Missing Keys/Types (High)
* **Missing Keys:** `items(playlists)` at `HomeScreen.kt:1879` lacks a `key` parameter, preventing Compose from reusing or animating items efficiently.
* **Missing `contentType`:** Across all lazy rows, columns, and horizontal grids on Home, `contentType` is omitted. Compose treats every item as a generic layout, discarding slot table reuse when recycling rows.

### 6. Song-Tap Path (High)
* **No Optimistic Feedback:** Tapping a song on Home does not immediately reflect active playing state. The UI waits for network queue resolution in `MusicService.playQueue` before `mediaMetadata` emits.
* **Synchronous Queue Preparation:** `player.setMediaItems` is passed the entire queue at once instead of windowing the tapped song and next 2-3 tracks lazily.

### 7. Expensive Drawing & Shaders (High)
* **Unconditional Blurs:** `MiniPlayer.kt:407` applies `.blur(60.dp)` on Android 12+ without checking device RAM or tier. `Player.kt:886` applies `.blur(100.dp to 150.dp)` inside an 800 ms `AnimatedContent` transition.
* **Unused Restraint Helper:** `Effects.kt` defines `Modifier.blurIfHighEnd(radius: Dp)` to protect low-RAM devices, but neither `Player` nor `MiniPlayer` calls it.

### 8. Image Loading (Medium–High)
* **Global Crossfade:** `App.kt:294` enables `crossfade(true)` globally on `SingletonImageLoader`. List scrolling suffers from compositor alpha blending on every recycled thumbnail.
* **RunBlocking Risk:** `App.kt:288-290` falls back to `runBlocking` in `newImageLoader` if `cachedCoilCacheSize` is unpopulated.

### 9. Build Configuration & Baseline Profiles (Medium–High)
* **Baseline Profile Wiring:** `app/src/main/baseline-prof.txt` exists, but `androidx.profileinstaller:profileinstaller` was not declared in Gradle dependencies. ART could not install compiled profiles ahead-of-time.
* **Benchmark Build Type:** Added in commit `75b7e46` (`app-foss-benchmark.apk` built at 27.27 MB vs 53.21 MB debug APK).

### 10. Startup Work (Medium)
* Eager initialization of `ListenTogetherManager` in `MainActivity.onCreate()` (line 371) runs prior to first composition.

### 11. Memory & GC Pressure (Medium)
* Large numbers of lambdas and filter/sort operations inside composition passes trigger repeated short-lived allocations and GC pauses during fast flings.

### 12. Debug Code in Release (Does Not Apply)
* Already verified as clean: debug logging is strictly guarded by `BuildConfig.DEBUG`, and stray logging statements were pruned in Phase 1.

---

## Action Plan for Phase B (Ordered by ROI)

1. **Fix Recomposition Storms on Home:**
   - Hoist `isPlaying` and `mediaMetadata` out of the root `HomeScreen`.
   - Pass derived lambdas or primitive booleans (`isCurrent: Boolean`) to song rows.
   - Remove per-item Room queries (`database.song()`, `database.speedDialDao.isPinned()`, `database.playlistByBrowseId()`) from lazy item composition; provide complete UI state models from `HomeViewModel`.
2. **Stop Never-Ending Work:**
   - Throttle or conditionally disable widget updates in `MusicService` (only update on real song/play state changes, never poll every 200 ms).
   - Cancel coroutine scopes and collectors in `PlayerConnection.dispose()`.
   - Throttle slider position updates to 4 Hz (250 ms) instead of 100 ms.
3. **Optimize Database Queries & Add Schema Index:**
   - Add an index on `Event.timestamp` and implement automatic event table pruning (keep latest 2,000 events).
4. **Stabilize List Structure & Add `key` / `contentType`:**
   - Provide explicit `key` and `contentType` across all `LazyColumn`, `LazyRow`, and `LazyHorizontalGrid` items.
5. **Drawing & Image Restraint:**
   - Disable Coil `crossfade` in list and grid items.
   - Guard all blurs with `Modifier.blurIfHighEnd` (disable blur on low-end devices and provide flat theme-aware surfaces).
6. **Optimistic Song-Tap Path:**
   - Apply immediate optimistic highlight on tapped song item before audio stream resolves.
