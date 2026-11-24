# TMusic v2 — Phase 0 Audit

**Date:** 2026-11-24
**Scope:** read-only static audit of `Tmusic-App/` plus the web landing page at the workspace root. No builds, tests, installs, commits, or feature changes were part of the audit. The only files changed in this session are the logo assets listed in §7.
**Method:** source/resource/Gradle inspection with targeted searches. No registry lookups were performed, so "outdated" below means visible indicators (alpha versions, numeric skew, JitPack tags), not verified latest-release comparisons. No runtime measurements were taken; `PERF_LOG.md` will be created in Phase 2 when before/after numbers exist.

---

## 1. Stack detection

**Classification: native Android application — Kotlin + Jetpack Compose, Gradle multi-module. Not React Native, Flutter, Capacitor, or a web wrapper.**

| Item | Finding | Evidence |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | `app/build.gradle.kts:26-33`, `app/src/main/kotlin/com/metrolist/music/MainActivity.kt:23`, `458-466` |
| Build system | Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.10, KSP 2.3.11 | `gradle/wrapper/gradle-wrapper.properties:3`, `gradle/libs.versions.toml:2,7,18`, `app/build.gradle.kts:28-32` |
| Modules | `:app` (application), `:innertube` (Android library) | `settings.gradle.kts:39-42`, `innertube/build.gradle.kts:1-12` |
| SDK / version | `compileSdk 37`, `minSdk 26`, `targetSdk 36`, `versionCode 152`, `versionName 13.6.3` | `app/build.gradle.kts:35-44` |
| JVM | Java 21 toolchain/target | `app/build.gradle.kts:149-160`, `innertube/build.gradle.kts:14-23` |
| Flavours | `foss`, `gms`, `izzy`; Cast is GMS-only | `app/build.gradle.kts:67-90`, `:289-292` |
| Player | Media3 / ExoPlayer 1.10.1, `MediaLibraryService`, `SimpleCache`, `ExoDownloadService` | `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt:264-268`, `388-416`; `AndroidManifest.xml:233-253` |
| Networking | Ktor 3.5.2 with OkHttp engine, kotlinx.serialization, protobuf | `innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt:138-190`; `app/build.gradle.kts:304-312` |
| Database | Room 2.8.4, 19 entities / 3 views, schema v38 | `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt:90-160` |
| Image loading | Coil 3.6.0 (Compose + OkHttp), SingletonImageLoader factory | `app/src/main/kotlin/com/metrolist/music/App.kt:16-24`, `298-327` |
| State / DI | ViewModels + StateFlow, Hilt 2.60.1, DataStore preferences | `app/src/main/kotlin/com/metrolist/music/di/AppModule.kt:32-109`, `utils/DataStore.kt:9-14` |
| Navigation | Compose Navigation (currently transitive only — see §9) | `ui/screens/NavigationBuilder.kt:20-25`, `69-100`; `MainActivity.kt:1252-1259` |
| WebView use | Narrow: YouTube login/cookie extraction and local po-token JS generation. **Not** the app shell | `ui/screens/LoginScreen.kt:114-144`, `utils/potoken/PoTokenWebView.kt:39-80` |

Excluded stacks: no `package.json`/React Native config, no `pubspec.yaml`, no `capacitor.config.*`, no KMP/iOS targets in Gradle (`settings.gradle.kts:39-42`).

---

## 2. Folder and architecture map

| Path | Role | Key evidence |
|---|---|---|
| `app/src/main/kotlin/com/metrolist/music/App.kt` | `Application`, Hilt entry, Coil factory, crash handling, DataStore init, Timber, prewarm | `App.kt:61-64`, `69-117`, `298-327` |
| `.../MainActivity.kt` | `FragmentActivity`, Compose host, service connection, deep links, update dialog | `MainActivity.kt:223-245`, `260-294`, `458-466`, `1252-1259` |
| `.../ui/` | Screens, player UI, components, menus, theme | `ui/screens/NavigationBuilder.kt:69-100`, `ui/theme/Theme.kt:13-26` |
| `.../playback/` | `MusicService`, `PlayerConnection`, downloads, queue, sleep timer, EQ | `MusicService.kt:264-268`; `PlayerConnection.kt:55-60`; `ExoDownloadService.kt:12-16` |
| `.../db/` | Room database, DAOs, entities, converters | `MusicDatabase.kt:90-160`, `DatabaseDao.kt` |
| `.../di/` | Hilt providers for DAOs, caches, players, Listen Together | `di/AppModule.kt:32-109` |
| `.../viewmodels/` | Home/account/library ViewModels | `viewmodels/HomeViewModel.kt`, `viewmodels/LibraryViewModels.kt:93-114` |
| `.../utils/` | Preferences, sync, updater, InnerTubeX bridge, po-token | `utils/Updater.kt:41-44`, `utils/potoken/PoTokenWebView.kt:39-80` |
| `.../recognition/` | Mic recognition foreground service | `AndroidManifest.xml:361-364`, `recognition/MusicRecognitionService.kt:12-18` |
| `.../widget/`, `.../quicksettings/` | Home-screen widgets, QS tile | `AndroidManifest.xml:291-376` |
| `.../listentogether/` | WebSocket + protobuf sync | `listentogether/ListenTogetherClient.kt:54-58`, `697-699` |
| `.../discord/` | OAuth, presence, gateway | `discord/DiscordAuth.kt:7,67`, `DiscordGateway.kt:13,80` |
| `.../eq/` | Equalizer UI/service/processors | `eq/audio/CustomEqualizerAudioProcessor.kt:3-6` |
| `.../api/`, `lyrics/`, `kugou/`, `lastfm/`, `lrclib/`, `paxsenix/`, `shazamkit/`, `betterlyrics/`, `imports/` | Metadata, lyrics, import services | `kugou/KuGou.kt:7-15`, `lrclib/LrcLib.kt:6-13`, `api/DeepLService.kt:11-15` |
| `innertube/src/main/kotlin/com/metrolist/innertube/` | InnerTube HTTP client, page parsers, models | `InnerTube.kt:138-230` |
| `app/src/main/proto/` | Listen Together schema | `listen_together.proto` (`app/src/main/proto/listentogether.proto:6,11,18,37`) |
| `app/src/{foss,gms,izzy}/` | Flavour-specific Cast stubs/implementations | `app/src/gms/kotlin/.../CastConnectionHandler.kt:5-13` |
| `app/src/test/` | JVM unit tests (JUnit, Robolectric, Ktor mock) | `db/MusicDatabaseMigrationTest.kt:13-22`, `discord/DiscordAuthTest.kt:21-29` |
| `app/src/main/res/` | ~80 locale folders, drawables, XML config | `res/values-*/` |

**Connection flow:** `App` → `MainActivity` binds `MusicService` via `ServiceConnection` → `PlayerConnection` exposes state flows → Compose UI collects them. Data flows through Hilt-provided DAOs and DataStore; `innertube` supplies remote metadata/streaming; `InnerTubeX` handles search/player/browse delegation (`InnerTube.kt:192-230`).

**Duplication / dead-code signals:** no explicit dead modules found, but `org.json:json` is declared then globally excluded (§3); `compose-shimmer` is used only in two places; `GlassBackground.kt` has no call sites; `tmusic_mark.png` was an unreferenced asset; `navigation-compose` is used in source but not declared (§9).

---

## 3. Dependency report

Versions are from `gradle/libs.versions.toml`. APK-size impact is qualitative; no dependency-insight run was performed.

| Alias | Module / version | Used | Notes |
|---|---|---|---|
| `guava` | `com.google.guava:guava` 33.7.1-jre | Yes | Medium/heavy. Needed for Media3 `ListenableFuture` interop. |
| `coroutines-guava` | `kotlinx-coroutines-guava` 1.11.0 | Yes | Small. Keep while Guava futures remain. |
| `gradle` | AGP 9.3.1 | Build-only | Verify against Gradle 9.7 / Kotlin 2.4.10. |
| `activity` | `activity-compose` 1.13.0 | Yes | Small/medium. |
| `hilt-navigation` | `hilt-navigation-compose` 1.4.0 | Yes | Small runtime; Android-only. |
| `datastore` | `datastore-preferences` 1.2.1 | Yes | Small; needs abstraction for iOS. |
| `compose-runtime` | Compose runtime 1.12.0 | Yes | Core; large. |
| `compose-foundation` | Compose foundation 1.12.0 | Yes | Large. |
| `compose-ui` / `compose-ui-util` | Compose ui / ui-util 1.12.0 | Yes | Large / small. |
| `compose-animation` | Compose animation 1.12.0 | Yes | Medium. |
| `compose-reorderable` | `sh.calvin.reorderable:reorderable` 3.1.0 | Yes | Small. |
| `viewmodel-compose` | `lifecycle-viewmodel-compose` 2.11.0 | Indirect | No direct import; verify graph. |
| `lifecycle-process` | `lifecycle-process` 2.11.0 | Yes | Small. |
| `material3` | `material3` **1.5.0-alpha27** | Yes | Large; high API-churn risk (alpha). |
| `materialKolor` | `material-kolor` 5.0.0 | Yes | Medium; replaceable by custom harmonisation. |
| `appcompat` | `appcompat` 1.8.0 | Yes | Medium/heavy legacy UI layer; migration cost to remove. |
| `browser` | `browser` 1.10.0 | Yes | Small; Custom Tabs for Discord OAuth. |
| `robolectric` | `robolectric` 4.16.1 | Tests only | No APK size. |
| `androidx-test-core` | `androidx.test:core` 1.7.0 | Tests only | No APK size. |
| `coil` | `coil-compose` 3.6.0 | Yes | Medium/high image stack. |
| `coil-network-okhttp` | `coil-network-okhttp` 3.6.0 | Declared; indirect | Verify whether required. |
| `ucrop` | `ucrop` 2.2.11 | Yes | Medium; Android-only cropper. |
| `shimmer` | `compose-shimmer` 1.5.0 | Yes | Small; used in two places. |
| `palette` | `palette` 1.0.0 | Yes | Small. |
| `media3` | `media3-exoplayer` 1.10.1 | Yes | High size; core playback. |
| `media3-okhttp` | `media3-datasource-okhttp` 1.10.1 | Yes | Medium. |
| `media3-session` | `media3-session` 1.10.1 | Yes | Medium; Android Auto/media controls. |
| `media3-cast` | `media3-cast` 1.10.1 | GMS only | Correctly flavour-gated. |
| `mediarouter` | `mediarouter` 1.8.1 | GMS only | Correctly flavour-gated. |
| `cast-framework` | `play-services-cast-framework` 22.3.1 | GMS only | Correctly flavour-gated. |
| `room-runtime` | `room-runtime` 2.8.4 | Yes | Medium; migration cost on iOS. |
| `room-compiler` | `room-compiler` 2.8.4 | Codegen | No APK size. |
| `hilt` | `hilt-android` 2.60.1 | Yes | Small runtime, heavy codegen; Android-only. |
| `hilt-compiler` | `hilt-android-compiler` 2.60.1 | Codegen | No APK size. |
| `ktor-client-core` | `ktor-client-core` 3.5.2 | Yes | Medium; **best KMP reuse candidate**. |
| `ktor-client-okhttp` | `ktor-client-okhttp` 3.5.2 | Yes | Android/JVM engine; Darwin on iOS. |
| `ktor-client-content-negotiation` | 3.5.2 | Yes | Small/medium; KMP candidate. |
| `ktor-client-encoding` | 3.5.2 | Yes | Small; KMP candidate. |
| `ktor-client-mock` | 3.5.2 | Tests only | No APK size. |
| `ktor-serialization-json` | 3.5.2 | Yes | Small/medium; KMP candidate. |
| `json` | `org.json:json` 20260522 | **Excluded globally** | Declared but excluded at `app/build.gradle.kts:243-251` to avoid R8/platform conflict; code uses platform `org.json` (`api/DeepLService.kt:14-15`). Remove or document. |
| `desugaring` | `desugar_jdk_libs_nio` 2.1.5 | Yes | Medium; needed for Java 21 source on minSdk 26. |
| `junit` | 4.13.2 | Tests only | No APK size. |
| `timber` | 5.0.1 | Yes | Tiny. |
| `innertubex` | `MetrolistGroup/innertubex` v0.5.2 | Yes, central | JitPack tag = supply-chain/upgrade risk; KMP availability must be checked. |
| `kuromoji-ipadic` | 0.9.0 | Yes | Dictionary can be size-heavy; iOS needs portable tokenizer. |
| `tinypinyin` | 2.0.3 | Yes | Small/medium; iOS equivalent needed. |
| `protobuf-javalite` / `protobuf-kotlin-lite` | 4.36.0 | Yes | Small/medium; good KMP candidate. |

Plugins: Compose compiler (Kotlin 2.4.10), Hilt 2.60.1, KSP 2.3.11, kotlinx.serialization (Kotlin 2.4.10), protobuf 0.10.0 (hardcoded — should be catalogued). Kotlin/KSP numeric skew (2.4.10 vs 2.3.11) needs verification before upgrades.

**Ad/tracker dependencies: none.** Full catalog at `gradle/libs.versions.toml:38-109`; web deps at `package.json:16-32` are UI/build only.

---

## 4. Ad and tracker scan

**Result: no advertising SDK, ad unit, tracker SDK, advertising-ID permission, or remote ad injection was found.**

Searched terms: AdMob, Unity Ads, AppLovin, Meta Audience Network, IronSource, StartApp, Mintegral, advertising ID, `ads`, `adunit`, `interstitial`, `banner`, `rewarded`, `admob`, Firebase Analytics/Crashlytics, Facebook SDK, Adjust, AppsFlyer, Mixpanel, Amplitude, Sentry, Plausible, Umami — across `*.kt`, `*.xml`, `*.kts`, `*.toml`, `*.json`, `*.js`, `*.ts(x)`, `*.html`, `*.md`.

False positives only:
- `res/values/metrolist_strings.xml:434` — "Adjust the song's tempo and pitch" (vendor name `Adjust`).
- `AndroidManifest.xml:34` — `android:banner="@mipmap/tv_banner"` is an Android TV banner.
- `walkthrough.md:65` — documentation copy "Open Source Banner".
- `playback/MusicService.kt:71-73`, `:1350`, `:3969` — `androidx.media3.exoplayer.analytics.*` is local playback analytics, not third-party tracking.
- `index.html:9-10`, `:15` — web copy explicitly claims ad-free behaviour.

**WebViews:** login WebView enables JS + JS interface for YouTube/Google login (`ui/screens/LoginScreen.kt:337-343`, `348-353`); po-token WebView enables JS but blocks network loads and runs a local asset (`utils/potoken/PoTokenWebView.kt:75-80`, `151-156`). No ad loading.

**Update checker (not an ad/tracker, but a recurring network call that the release gate treats separately):**
- `app/build.gradle.kts:74` (`foss` true), `:81` (`gms` true), `:88` (`izzy` false).
- `constants/PreferenceKeys.kt:102-103`, `MainActivity.kt:481`, `ui/screens/settings/UpdaterSettings.kt:59-60` — default enabled.
- `MainActivity.kt:485-493`, `:506-520`, `:527-529`, `:1359-1365` — automatic check + notification/dialog.
- `utils/Updater.kt:41-44` — 2-hour interval, GitHub API URLs, `Metrolist.apk` asset name.
- UI: `ui/screens/settings/SettingsScreen.kt:212-218`, `:236-264`; `AccountSettings.kt:406-417`; `ChangelogScreen.kt:49-53`; `ui/component/ReleaseNotesCard.kt:27`; `UpdaterSettings.kt:59-83`, `:140-153`, `:188-217`.

**Placeholders:** no `[UPI_ID]`, FIXME, lorem ipsum, dummy, TBD, "coming soon", or "your name here" in production text. `example.com` appears only in tests. Remaining "placeholder" hits are intentional UI hints and technical comments (`metrolist_strings.xml:510,804,1038`; `PlaylistExporter.kt:134-136`; `ShazamSignatureGenerator.kt:263`; GitHub issue forms).

**Release/debug leftovers:**
- `App.kt:90-91` — unconditional `Timber.DebugTree()` in `Application.onCreate()` (no `BuildConfig.DEBUG` guard).
- `ui/screens/playlist/AutoPlaylistScreen.kt:330-344` — four `[UPLOAD_DEBUG]` `println` calls; `innertube/YouTube.kt:2093`, `:2164` — `println` of renderer data on errors.
- `assets/po_token.html:123-190` — ten `console.log` calls forwarded to Timber at `PoTokenWebView.kt:82-90`.
- `app/build.gradle.kts:173-181` — release lint disabled (`abortOnError = false`, `checkReleaseBuilds = false`).
- `res/xml/network_security_config.xml:3-9` plus `AndroidManifest.xml:40` — cleartext traffic globally permitted (comment: local Listen Together servers).
- Release itself is non-debuggable (`app/build.gradle.kts:124`); no `android:debuggable=true`, StrictMode, or LeakCanary in release.
- Debug keystore credentials hardcoded in debug-only config (`app/build.gradle.kts:19-22`, `:93-98`, `:111-115`); release credentials use env vars (`:105-109`).

---

## 5. Performance hotspot scan

Static findings ranked by likely impact after months of daily use. **No runtime measurements exist yet** — Phase 2 must establish baselines in `PERF_LOG.md`.

| # | Finding | Evidence | Impact | Effort |
|---:|---|---|---|---|
| 1 | Listen-history/recognition/search `Event` rows inserted on every qualifying playback-stats callback; only full `DELETE` APIs exist; history/stat queries scan all events and lack a `timestamp` index; timestamped DB backups also never prune. | `MusicService.kt:3968-3990`; `db/entities/Event.kt:16-31`; `DatabaseDao.kt:1473-1501`, `1511-1516`, `1519-1533`, `499-515`; `MusicDatabase.kt:260-290`, `322-339` | Very high | Medium |
| 2 | `PlayerConnection` collectors and `stateIn` flows leak on each service reconnect: a new connection is created in `onServiceConnected`, but `dispose()` only removes the player listener. | `playback/PlayerConnection.kt:137-144`, `167-178`, `210-216`, `670-678`; `MainActivity.kt:267-279`, `282-294` | Very high | Low–medium |
| 3 | Listen Together holds a partial wakelock continuously in a room (refreshed every 25 s, 10-min timeout); background idle disconnect disabled while connected. | `listentogether/ListenTogetherClient.kt:236-240`, `357-363`, `810-827`, `858-884` | High (battery/thermal) | Medium |
| 4 | Widget refresh polls every 200 ms while playing and rebuilds all RemoteViews, even with no widget installed. | `MusicService.kt:2721-2729`, `4580-4614`, `4619-4629`; `widget/TmusicWidgetManager.kt:52-97` | High | Low–medium |
| 5 | Main library UI collects full-list flows and sorts/filters in memory despite paged DAO overloads existing. | `DatabaseDao.kt:136-189`, `191-243`, `249-263`, `719-751`, `1007-1030` (paged at `145-146`, `200-201`, `257-259`, `265-270`); `viewmodels/LibraryViewModels.kt:93-114`, `147-171` | High | Medium–high |
| 6 | Unbounded/effectively unbounded caches: stream URL generations, lyrics translation cache, player gradient-colour cache, `NoOpCacheEvictor` download cache. | `playback/StreamUrlCache.kt:37-51`, `111-120`; `lyrics/LyricsTranslationHelper.kt:50`, `86-100`, `160-163`; `ui/player/Player.kt:413-416`, `461`; `di/AppModule.kt:74-78`, `92-95` | Medium–high | Low–medium |
| 7 | Main-thread blocking `runBlocking` on cold-start/player creation; `MusicService.onCreate` creates the player on main. | `App.kt:94-97`, `298-301`; `MusicService.kt:626`, `678`, `1294-1299`, `1342-1347`; `DatabaseDao.kt:1554-1565` | Medium | Low |
| 8 | High-frequency loops (100 ms player position, 8 ms lyrics, 50 ms infinite indicators) and large blurs (100–150 dp player, 60 dp mini-player, 25–30 animated Wrapped elements). | `ui/player/Player.kt:760-770`, `858-886`; `ui/component/OriginalLyrics.kt:557-570`, `1580-1591`; `PlayingIndicator.kt:53-62`; `MiniPlayer.kt:398-408`; `wrapped/components/AnimatedBackground.kt:56-67` | Medium | Low |
| 9 | Full-size/oversized image decode for lyrics palette and share image (1024 px cover, 2160×2160 output, stack blur, new `ImageLoader` instances). | `LyricsImageCard.kt:187-195`; `LyricsComponents.kt:320-329`; `ComposeToImage.kt:63-68`, `94-103`, `120-141`, `389-419` | Medium | Low–medium |
| 10 | R8 weakened by broad `-keep` rules; `baseline-prof.txt` exists but no ProfileInstaller/macrobenchmark wiring. | `app/build.gradle.kts:121-128`, `253-322`; `app/proguard-rules.pro:128-130`, `169-172`, `174-182`, `185-188`; `app/src/main/baseline-prof.txt:1-5` | Medium | Low |

**Already-correct lifecycle handling (keep):** player recreation removes listeners and releases old player (`MusicService.kt:964-989`); crossfade cleanup releases secondary player (`4739-4767`, `4778-4817`, `4880-4899`); `onDestroy` unregisters receivers/callbacks/observers (`4194-4233`); Compose `Player.Listener` disposed via `DisposableEffect` (`MainActivity.kt:856-876`); lyrics translation jobs cancelled on dispose (`OriginalLyrics.kt:367-370`, `ExperimentalLyrics.kt:233-236`). Exception: `WrappedAudioService` releases its player but never cancels its `CoroutineScope` (`WrappedAudioService.kt:30-43`, `78-80`, `139-143`). No static Activity/Context leaks found.

---

## 6. Animation inventory

No Lottie, parallax, or animated-shadow references found. Verdicts: **keep (functional)** / **simplify** / **remove (decorative)**.

| Area | Verdict | Evidence |
|---|---|---|
| Central motion specs, navigation/screen transitions, shared-item reveals, player control feedback, dialogs/gestures, loading shimmer, audio crossfade, recognition states, Wrapped reveal/counters | **Keep (functional)** | `ui/theme/Animations.kt:19-58`; `MainActivity.kt:965-968`, `1260-1298`; `NavigationBuilder.kt:159-178`; `Items.kt:1509-1512` et al.; `MusicService.kt:4688-4707`; `ui/component/shimmer/ShimmerHost.kt:34-36` |
| Functional static gradients (fading edges, readability scrims, recognition border) | **Keep (functional)** | `ui/utils/FadingEdge.kt:27-72`; `MiniPlayer.kt:417-429`; `Player.kt:927`; `HomeScreen.kt:561-577` |
| Decorative grid-item animation | **Simplify** | `ui/component/RandomizeGridItem.kt:35,41` |
| Playing indicator / equaliser bars (50 ms infinite loop) | **Simplify** | `ui/component/PlayingIndicator.kt:49-63`, `95-98` |
| Player art fade + 100–150 dp blur; mini-player 60 dp blur not gated by `DeviceTier`; lyrics card 50 dp blur ×2; share export blur + 2160² bitmap | **Simplify** | `Player.kt:858-886`, `900-903`; `MiniPlayer.kt:398-408`; `LyricsImageCard.kt:227-236`, `263-277`; `ComposeToImage.kt:120-141`, `389-419` |
| Device-tier blur helper exists but heavy blur call sites bypass it | **Keep helper, apply it** | `ui/component/Effects.kt:27-29` |
| Decorative infinite Wrapped backgrounds (25–30 elements), rotating shapes, intro scale/rotation | **Remove (decorative)** | `wrapped/components/AnimatedBackground.kt:56-67`; `AnimatedDecorativeElement.kt:26-40`; `WrappedIntro.kt:79-97`; call sites `WrappedTop5SongsScreen.kt:61`, `ConclusionPage.kt:32`, `AlbumPages.kt:76,158,236` |
| Continuous lyric-line pulse | **Remove (decorative)** | `OriginalLyrics.kt:1563-1591` |
| Unused `GlassBackground` (PixelCopy/Choreographer 25 fps loop + decorative gradient) | **Remove (decorative)** | `ui/component/GlassBackground.kt:63`, `93-107`, `151-200` |
| Large static thumbnail shadows (16–24 dp) | **Remove (decorative)** | `AlbumScreen.kt:206-210`; `LocalPlaylistScreen.kt:1058-1061`, `1084-1088`, `1153`; `AutoPlaylistScreen.kt:865-868`; `CachePlaylistScreen.kt:493`; `OnlinePlaylistScreen.kt:513`; `TopPlaylistScreen.kt:550` |

Phase 3 must also respect the system reduce-motion setting, add an in-app "Reduce animations" toggle default-on for low-RAM devices, cap remaining durations at 150–200 ms, and animate only transform/opacity.

---

## 7. Logo and branding inventory

Source file provided and confirmed by the user: `C:\Projects\TMUSIC APP + WEB\tmusic new logo.jpg` (1254×1254, black background, white/grey ribbon). Description in the master plan matches: white flowing ribbon forming a stylised T on black.

### Original occurrences (inventory before this session's logo change)

| Area | Files / references |
|---|---|
| Launcher icons | `AndroidManifest.xml:38,41,169,171,183,185`; `res/mipmap-anydpi-v26/ic_launcher*.xml:1-6`; `res/mipmap-anydpi-v31/ic_launcher*.xml:1-6`; layers `@color/ic_launcher_background`, `@drawable/ic_launcher_foreground`, `@drawable/ic_launcher_monochrome`; background `#000000` at `res/values/ic_launcher_background.xml:3` |
| Density assets | `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_static.png`, `ic_launcher_static_round.png`; `res/drawable-{*,}/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`, `ic_launcher_static_foreground.png` |
| Store listing | `app/src/main/ic_launcher-playstore.png`, `assets/ic_launcher-playstore.png`, `fastlane/metadata/android/en-US/images/icon.png`; `README.md:3`; title `fastlane/.../title.txt:1` = TMUSIC |
| In-app | `AboutScreen.kt:86,143-145` (`tmusic_logo`); `WrappedIntro.kt:137-139` (`app_logo`); `ConclusionPage.kt:39-42` (`ic_launcher_foreground`); `MusicService.kt:640,4136`, `Player.kt:989`, `Thumbnail.kt:605`, `LyricsImageCard.kt:406`, `ComposeToImage.kt:282` (`small_icon`); unreferenced `res/drawable/tmusic_mark.png` |
| Widgets | `res/xml-v31/{music,playlist,recognizer,turntable}_widget_info.xml` preview images |
| Web landing page | `index.html:17` (og:image), `:20` (favicon) → `/images/tmusic-logo.svg`; `src/config.ts:19-23`; `src/components/LogoImage.tsx:11-15`, `31-44`, `48-69`; usages in `HeroSection.tsx:63`, `FinalCTASection.tsx:31`, `Footer.tsx:60`, `Navigation.tsx:66`, `LoadingScreen.tsx:56`; assets `public/images/tmusic-logo.{png,jpeg,svg}`, `public/favicon.{png,svg}`, `public/downloads/TMUSIC.apk` |
| TV banner | `res/mipmap-xhdpi/tv_banner.png`, `AndroidManifest.xml:34` — separate TV asset, not the launcher logo |

### Applied in this session (user-approved logo-only change)

51 files regenerated from the supplied JPEG (no redrawing). Key density assets: adaptive foreground/monochrome/static layers at 108/162/216/324/432 px; legacy `ic_launcher`/`ic_launcher_round` WebP and static PNGs at the same five densities; three 512 px store icons (all SHA-256 prefix `a660ff47b5ea3688`); in-app `app_logo.png`, `tmusic_logo.png`, `tmusic_mark.png`, `small_icon.png`; web `tmusic-logo.png/.jpeg/.svg`, `favicon.png/.svg`; preview sheet `C:\Projects\TMUSIC APP + WEB\icon-preview.png`.

Measured alignment (not eyeballed): source glyph bbox `(104,101)-(1151,1124)`; foreground glyph bbox `(25,26)-(85,84)` on the 108 dp canvas; maximum axis offset **31.5 dp** vs the 33 dp half-width of the 66 dp safe square; maximum radial distance **42.48 px** vs the 54 px full-canvas mask radius; visual mass centre after a capped optical nudge **(53.93, 53.28)** dp vs canvas centre (53.5, 53.5). Monochrome layer is white-only and its alpha mask matches the foreground.

Verification run: `.\gradlew.bat :app:assembleFossDebug --console=plain --no-daemon` → `BUILD SUCCESSFUL` (24 s); `npm run build` → built in 1.97 s. APK SHA-256 `26ed74d3c9084c12dc05a46c51a1567219a7418af28a92587674b688ef8ce34e`. **The preview sheet still needs the user's visual approval.**

### Branding leftovers still present (not changed — need approval in Phase 4)

- Package/namespace `com.metrolist.music` (`app/build.gradle.kts:10,36`); env override `METROLIST_APP_NAME` (`:12`).
- `res/values/strings.xml:6` comment about Metrolist; `metrolist_strings.xml:252` GitHub releases URL `MetrolistGroup/Metrolist/releases`; `:379-380` "Metrolist KMP"; `:413`, `:471`, `:476`, `:544`, `:550`, `:713`, `:765`, `:773`, `:789` Metrolist user-facing text.
- `utils/Updater.kt:42-44` GitHub API URLs and `Metrolist.apk` asset name.
- `wrapped_logo_content_description` still "Metrolist Logo" (`metrolist_strings.xml:471`).
- Favicon duplication: `index.html:20` uses `/images/tmusic-logo.svg` while `/favicon.svg` and `/favicon.png` exist unreferenced (both now contain the new logo).

**No splash-screen logo asset exists.** `res/values/styles.xml:3,9` defines `Theme.Tmusic` with a solid white window background; Android 12+ uses the launcher icon for the system splash screen, so the regenerated launcher icon covers it. A dedicated splash logo would be a new visual element and needs approval.

---

## 8. iOS readiness

**Current state:** no iOS project, no KMP targets, no `Podfile`/`Package.swift`/`capacitor.config.*` anywhere in the repo. Host is Windows with no Mac/Xcode/Apple credentials, so no native iOS binary can be compiled, signed, or tested here. macOS CI must be used for any real build.

**Blockers:** Compose UI, Media3 playback service/foreground service/Android Auto, Room v38 with 19 entities, DataStore, Hilt, WebView po-token flow, widgets/QS tile/mic recognition, and ~80 Android locale folders have no direct iOS equivalent. Portable pieces: Ktor clients, kotlinx.serialization models, protobuf schema, Innertube page models/parsers, pure utilities.

| Option | Effort | Risk | Performance | Reuse |
|---|---|---|---|---|
| **A. Native SwiftUI/UIKit rewrite** | Very high | High for parity | Best if done well with AVFoundation | API contracts, protobuf, strings only |
| **B. KMP shared core + native iOS UI/playback** (recommended) | High, lower than A | Medium — Room/Hilt/Media3/DataStore/WebView coupling | Good; native playback and UI on each platform | Models, Ktor, serialisation, protobuf, pure utils |
| **C. Web wrapper / Capacitor** | Medium initially, high ongoing | Very high — background audio, offline cache, po-token, App Store policy | Worst of the three | Almost none |

**Recommendation:** Option B. First evaluate the upstream `Metrolist-KMP` effort referenced in `changelog.md:9,17` before building a parallel shared core. iOS must implement AVAudioSession playback category, background audio mode, MPNowPlayingInfoCenter, MPRemoteCommandCenter, headphone/Bluetooth controls, interruption and route-change handling, AirPlay, privacy manifest, and a documented, licensed music source for App Store review.

---

## 9. Prioritised fix list

Ranked by impact on 2 GB / Android Go class devices, then effort. **Awaiting user approval before any of these are implemented.**

| Priority | Fix | Why | Phase |
|---:|---|---|---|
| 1 | Prune `Event` history on startup/periodically with an index on `timestamp`; cap backups | Directly addresses months-long slowdown (§5 #1) | 2 |
| 2 | Cancel `PlayerConnection` collectors/hot flows on dispose; scope to lifecycle | Stops cumulative duplicate queries/listeners (§5 #2) | 2 |
| 3 | Drop/conditionally gate the 200 ms widget refresh and partial wakelock | Battery/thermal and CPU churn (§5 #3, #4) | 2 |
| 4 | Bound caches (images ~100 MB, audio ~500 MB configurable, API responses by TTL+size), add Clear cache UI | Prevents unbounded growth (§5 #6) | 2 |
| 5 | Use paged DAO overloads in library ViewModels; stable keys; debounce search 300 ms | Removes in-memory full-list sorts (§5 #5) | 2 |
| 6 | Move `runBlocking` and player creation off main; throttle progress updates to ≤4 Hz | Cold start and scroll jank (§5 #7, #8) | 2 |
| 7 | Decode thumbnails at display size; reuse one image loader; cancel off-screen loads | Memory spikes (§5 #9) | 2 |
| 8 | Remove decorative Wrapped infinite animations, lyric pulse, unused `GlassBackground`, large static shadows; apply `DeviceTier` blur helper | GPU/battery (§6) | 3 |
| 9 | Gate remaining motion behind reduce-motion setting + in-app toggle; 150–200 ms transform/opacity only | Plan requirement | 3 |
| 10 | Remove ads/trackers if any appear later; **remove/disable the update checker for the v2 release** and re-scan merged manifest | Release gate requires zero ad/tracker/update-check code (§4) | 1 / release |
| 11 | Guard `Timber.DebugTree()` and remove `println`/`console.log` leftovers; re-enable release lint; restrict cleartext traffic | Release hygiene (§4) | 1 / release |
| 12 | Replace remaining Metrolist strings/URLs/IDs if user approves; rename content descriptions | Branding consistency (§7) | 4 |
| 13 | Tighten broad ProGuard `-keep` rules; wire real baseline profile generation + ProfileInstaller | Smaller DEX, faster start (§5 #10) | 2 |
| 14 | Declare `navigation-compose` explicitly, remove/document unused `org.json:json`, verify Kotlin/KSP, catalogue protobuf plugin | Build stability (§3) | 2 |
| 15 | Soak test + 6-month simulation + `PERFORMANCE.md` before release | Definition of done | 5 |

---

## Phase 0 exit status

- Audit produced: **yes** (this file).
- User approval of fix list: **pending — stopped here**.
- Performance before/after numbers: **not measured** (static audit only).
- Release Step 0 gate: **not passed** — tree is not clean/release branch not created; lint/tests not run; update-check code present; debug logging leftovers present; old Metrolist branding remains; performance budgets not yet measured. These are tracked above and must be closed before any signed v2 build.
- iOS: **not buildable on this host**; macOS CI workflow and secrets checklist will be prepared when the user approves the release path.
