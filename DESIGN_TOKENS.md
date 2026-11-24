# TMusic design tokens (read-only reference)

Generated from the **current** Compose theme on 2026-11-24, before Phase 1 changes. This is a documentation snapshot only — **no colour, shape, type or spacing value was changed**. When a value is edited in code, update this file in the same commit.

Sources:
- `app/src/main/kotlin/com/metrolist/music/ui/theme/Theme.kt` — palettes, typography, shapes, theme selection
- `app/src/main/kotlin/com/metrolist/music/ui/theme/Font.kt` — font families
- `app/src/main/kotlin/com/metrolist/music/ui/theme/Animations.kt` — motion specs (reference)
- `app/src/main/kotlin/com/metrolist/music/constants/Dimensions.kt` — central dp constants
- `app/src/main/kotlin/com/metrolist/music/ui/theme/PlayerSliderColors.kt` — slider colour derivation

## 1. Colour

The identity is deliberately monochrome: black / white / grey everywhere. Red is reserved for errors. `themeColor` is accepted by the theme composable for settings compatibility but never tints the palette.

`DefaultThemeColor = #FFED5564` (`Theme.kt:33`) — sentinel only; no visual tint.

### Dark scheme — `TmusicDarkScheme` (`Theme.kt:40-77`)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#FFFFFFFF` | onPrimary | `#FF000000` |
| primaryContainer | `#FF1E1E1E` | onPrimaryContainer | `#FFFFFFFF` |
| secondary | `#FF9E9E9E` | onSecondary | `#FF000000` |
| secondaryContainer | `#FF1A1A1A` | onSecondaryContainer | `#FFD0D0D0` |
| tertiary | `#FFB4B4B4` | onTertiary | `#FF000000` |
| tertiaryContainer | `#FF262626` | onTertiaryContainer | `#FFE6E6E6` |
| background | `#FF000000` | onBackground | `#FFE6E6E6` |
| surface | `#FF121212` | onSurface | `#FFE6E6E6` |
| surfaceVariant | `#FF1E1E1E` | onSurfaceVariant | `#FF9E9E9E` |
| surfaceTint | `#FFFFFFFF` | scrim | `#FF000000` |
| inverseSurface | `#FFE6E6E6` | inverseOnSurface | `#FF1A1A1A` |
| inversePrimary | `#FF000000` | | |
| surfaceDim | `#FF000000` | surfaceBright | `#FF2E2E2E` |
| surfaceContainerLowest | `#FF0A0A0A` | surfaceContainerLow | `#FF121212` |
| surfaceContainer | `#FF1A1A1A` | surfaceContainerHigh | `#FF242424` |
| surfaceContainerHighest | `#FF2E2E2E` | | |
| outline | `#FF2C2C2C` | outlineVariant | `#FF1E1E1E` |
| error | `#FFCF6679` | onError | `#FF000000` |
| errorContainer | `#FF8C1D18` | onErrorContainer | `#FFF9DEDC` |

### Light scheme — `TmusicLightScheme` (`Theme.kt:80-117`)

| Role | Hex | Role | Hex |
|---|---|---|---|
| primary | `#FF000000` | onPrimary | `#FFFFFFFF` |
| primaryContainer | `#FFE0E0E0` | onPrimaryContainer | `#FF000000` |
| secondary | `#FF757575` | onSecondary | `#FFFFFFFF` |
| secondaryContainer | `#FFE6E6E6` | onSecondaryContainer | `#FF333333` |
| tertiary | `#FF616161` | onTertiary | `#FFFFFFFF` |
| tertiaryContainer | `#FFEEEEEE` | onTertiaryContainer | `#FF424242` |
| background | `#FFFFFFFF` | onBackground | `#FF1A1A1A` |
| surface | `#FFF5F5F5` | onSurface | `#FF1A1A1A` |
| surfaceVariant | `#FFEAEAEA` | onSurfaceVariant | `#FF757575` |
| surfaceTint | `#FF000000` | scrim | `#FF000000` |
| inverseSurface | `#FF1A1A1A` | inverseOnSurface | `#FFF0F0F0` |
| inversePrimary | `#FFFFFFFF` | | |
| surfaceDim | `#FFE0E0E0` | surfaceBright | `#FFFFFFFF` |
| surfaceContainerLowest | `#FFFFFFFF` | surfaceContainerLow | `#FFF5F5F5` |
| surfaceContainer | `#FFF0F0F0` | surfaceContainerHigh | `#FFEAEAEA` |
| surfaceContainerHighest | `#FFE0E0E0` | | |
| outline | `#FFDCDCDC` | outlineVariant | `#FFEDEDED` |
| error | `#FFB3261E` | onError | `#FFFFFFFF` |
| errorContainer | `#FFF9DEDC` | onErrorContainer | `#FF410E0B` |

### Pure-black AMOLED scheme — `TmusicBlackScheme` (`Theme.kt:124-140`)

`TmusicDarkScheme.copy(...)` with every surface slot set to `#FF000000`: `background`, `surface`, `surfaceVariant`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`. Text/accent/error roles stay as dark; `outline = #FF2C2C2C`, `outlineVariant = #FF1A1A1A` provide 1px card separation.

### Derived contextual colours

- Player slider (`PlayerSliderColors.kt:37-57`): inactive track = `outline` at 40% alpha (dark/default) or `onSurface` at 30% alpha (light/default); `Color.White` at 40% alpha for BLUR/GRADIENT player backgrounds. Active track/ticks/thumb use the caller's `activeColor`.
- Mini-player error override: `#FFFF6B6B` when `forceLightColors`, otherwise `colorScheme.error` (`ui/player/MiniPlayer.kt:309`).
- Lyrics image card default background: `#FF121212` dark / `#FFF5F5F5` light (`ui/component/LyricsImageCard.kt:170`).

## 2. Typography

`TmusicTypography` (`Theme.kt:147-181`) is `MaterialTheme.Typography()` with **only `fontFamily` overridden** on all 30 style slots. Font size, weight, line height and letter spacing are the Material 3 defaults from the `material3 1.5.0-alpha27` dependency — no numeric type tokens are overridden in this codebase.

Family (from `Font.kt:8-25`):

| Token | Actual family | Weights provided |
|---|---|---|
| `Inter` | bundled Inter | Normal, Medium, SemiBold, Bold, ExtraBold, Black (`R.font.inter_*`) |
| `bbhBartle`, `bbh_bartle` | aliases of `Inter` | kept for compatibility; `TmusicTypography` uses `bbhBartle` |
| `InterDisplay` | bundled Inter Display | Bold, SemiBold→`inter_bold`, Normal (`R.font.inter_regular`) — defined but not used by `TmusicTypography` |

Style slots with `fontFamily = bbhBartle`:
`displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge`, `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelMedium`, `labelSmall`, plus the matching `*Emphasized` variants of all 15.

## 3. Spacing and sizes

Central dp constants (`constants/Dimensions.kt:20-38`):

| Token | dp | Token | dp |
|---|---:|---|---:|
| `NavigationBarHeight` | 80 | `SlimNavBarHeight` | 64 |
| `MiniPlayerHeight` | 64 | `MinMiniPlayerHeight` | 16 |
| `MiniPlayerBottomSpacing` | 8 | `QueuePeekHeight` | 64 |
| `AppBarHeight` | 64 | `ListItemHeight` | 64 |
| `SuggestionItemHeight` | 56 | `SearchFilterHeight` | 48 |
| `ListThumbnailSize` | 48 | `ThumbnailCornerRadius` | 3 |
| `SmallGridThumbnailHeight` | 104 | `GridThumbnailHeight` | 128 |
| `AlbumThumbnailSize` | 144 | `PlayerHorizontalPadding` | 32 |

Additional spacing is currently ad hoc rather than tokenised; the recurring values in components are 8, 12, 16, 24 and 32 dp. These are **not** central tokens — treat `Dimensions.kt` as the source of truth and do not promote ad hoc values without a design change.

Spring specs (`Dimensions.kt:40-53`): `NavigationBarAnimationSpec` and `BottomSheetAnimationSpec` use `Spring.DampingRatioNoBouncy` + `Spring.StiffnessMediumLow`; `BottomSheetSoftAnimationSpec` uses `NoBouncy` + `StiffnessLow`.

## 4. Shapes

Theme token `TmusicShapes` (`Theme.kt:184-190`):

| Token | Corner radius |
|---|---:|
| `extraSmall` | 8 dp |
| `small` | 8 dp |
| `medium` | 12 dp |
| `large` | 12 dp |
| `extraLarge` | 16 dp |

Common component-level overrides (not theme tokens, listed for completeness): thumbnails `RoundedCornerShape(3.dp)` via `ThumbnailCornerRadius`; chips and menu cards 16 dp; dialogs and loading cards 28 dp; settings groups / integration cards 24 dp; navigation title 12 dp; bottom sheets 2 dp or top-only corner radius; mini-player art 32 dp; pill buttons 50 dp; lyrics cards 16 dp.

## 5. Motion (reference only — Phase 3 will act on this)

`MotionDefaults` (`Animations.kt:19-37`): `DURATION_SHORT = 160 ms`, `DURATION_MEDIUM = 220 ms`, `DURATION_LONG = 300 ms`, `CROSSFADE_MS = 300 ms`, `SPRING_STIFFNESS = 380f`, `SPRING_DAMPING = 0.8f`. `standardTween()` uses medium duration + `FastOutSlowInEasing`; `emphasisSpring()` uses the damping/stiffness above. Phase 3 must reconcile these with the master plan's 150-200 ms / reduce-motion rules — this snapshot records the current values, not the target.

## 6. Theme selection

`MetrolistTheme` (`Theme.kt:192-216`) picks `TmusicBlackScheme` when `darkTheme && pureBlack`, `TmusicDarkScheme` when `darkTheme`, otherwise `TmusicLightScheme`, then supplies `TmusicTypography` and `TmusicShapes` to `MaterialTheme`. `TmusicTheme` (`Theme.kt:218-224`) is a thin alias. Do not change either signature while documenting tokens.
