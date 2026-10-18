# Ritual — Design System (Stage 5b: Apple-inspired, monochrome)

Principle: **quiet sophistication — every detail earns its place.** Calm, premium and tactile, as a
native Android interpretation of Apple's craft, not a copy of iOS. Strictly monochrome.
Source of truth: `app/src/main/kotlin/app/ritualhabits/ui/theme/` and `ui/components/`.
Access tokens through `Ritual.colors`, `Ritual.motion`, `Ritual.haptics`, `Ritual.layout`.

This replaces the Stage 5a editorial direction.

## Colour
Every token is a neutral grey (R = G = B). Contrast is tested in `DesignSystemTest`. The two
themes are designed separately:

| Token | Light | Dark | Role |
|---|---|---|---|
| background | #F4F4F4 | #000000 | page |
| surface | #FFFFFF | #1B1B1B | grouped lists, summary card, fields |
| surfaceRaised | #FFFFFF | #363636 | segmented thumb |
| fill | #E9E9E9 | #2B2B2B | tonal buttons, tracks, selected-tab pill |
| glass | white 92 % | #1E1E1E 92 % | floating tab bar only |
| glassEdge | white | white 18 % | top-edge highlight on glass |
| textPrimary | #111111 | #F4F4F4 | ≥ 7:1 everywhere |
| textSecondary | #636363 | #A3A3A3 | ≥ 4.5:1 on every surface and on glass over worst-case content |
| textTertiary | #A3A3A3 | #6A6A6A | decorative only |
| border / borderStrong | #E3E3E3 / #C2C2C2 | #2E2E2E / #555555 | separators / pending rings |
| accent / onAccent | ink / white | near-white / black | filled controls, completion marks |

Hierarchy comes from tone (white cards on a grey page; charcoal on black), weight and spacing.
There are no colour accents, coloured states or gradients. The only gradient is the glass edge
highlight fading out.

## Typography: Outfit
Bundled as `res/font/outfit_variable.ttf` (variable `wght`, SIL OFL 1.1). The licence and source are
in `assets/licenses/`. There are no runtime downloads. Regular is the lightest weight in use.
Outfit's extra cap padding is trimmed (`LineHeightStyle.Trim.Both`), so text aligns optically
with icons.

| Style | Size / weight | Use |
|---|---|---|
| display | 64 Medium, tabular | focus countdown |
| title | 30 SemiBold | screen titles |
| numeral | 28 SemiBold, tabular | large counts |
| heading | 20 SemiBold | section titles, summary headline, empty-state titles |
| value | 17 SemiBold, tabular | ring count, sheet titles |
| rowTitle | 17 Regular | habit names, list rows |
| body / bodySmall | 16 / 15 Regular | copy / descriptions |
| label | 16 SemiBold | buttons |
| overline | 14 Medium | eyebrow date, small group labels, segmented labels |
| caption | 13 Regular | metadata, tab labels, week letters |

Everything is in `sp` and follows the system font size with no caps. The only text that shrinks to
fit is the decorative count inside the progress ring, which is duplicated in the section header and
the ring's accessibility label. Layouts reflow at large sizes; the tab bar shows only the selected
label when labels no longer fit, and every tab keeps its accessible name.

## Space, shape, size
- Spacing: 2 · 4 · 8 · 12 · 16 · 24 · 32 · 48 dp.
- Screen gutters: 16 dp (< 360 dp), 20 dp (phones), 32 / 40 dp on larger screens, with content centred
  at 600 / 680 dp.
- Radii: 10 (thumbs) · 14 (menus, segmented track, snackbars) · 22 (grouped surfaces) · 30 (sheets,
  dialogs, tab bar). Buttons are capsules.
- Touch targets ≥ 48 dp. Habit rows ≥ 60 dp. Primary buttons are 52 dp tall. Icon buttons have a 40 dp
  tonal disc in a 48 dp target.
- Borders are avoided. Separators are hairlines inset to align with row text.

## Materials: restrained "liquid glass"
Glass is used **only** for the floating tab bar (`Modifier.glassSurface`):
- 92 % opaque neutral fill. Content scrolling beneath reads as a soft tonal shift. Labels on the bar
  are tested at ≥ 4.5:1 even over pure black (light) or pure white (dark) content behind them.
- A delicate edge: a white top highlight fading out by mid-height, plus a faint lower hairline.
- A very soft, wide shadow in light mode. No shadow in dark mode, where the edge highlight does the work.
- **No backdrop blur.** Compose has no supported API for blurring content *behind* a surface without a
  third-party library (e.g. Haze), and the material must look right without blur anyway. It renders
  identically on every Android version, with no extra GPU passes.

All other surfaces are opaque.

## Motion
| Spec | Value | Use |
|---|---|---|
| quick | 140 ms | colour and fill changes, press feedback |
| standard | 240 ms | content changes, tab cross-fade |
| emphasized | 320 ms | pushed screens |
| settle | spring, damping 0.82 | completion check, progress ring, sliding thumb, tab pill (no visible bounce) |
| emphasis | spring, damping 0.62 | only the habit that completes the day |

Press feedback is a 0.96 scale. Nothing blocks taps or shifts layout. When the system
"Remove animations" setting is on, every spec snaps (verified on the emulator).

## Haptics
Semantic events (Selection, ToggleOn/Off, Confirm, Reject) are fired from ViewModel events after
the database confirms a change, never from recomposition. They are gated by Ritual's preference.

## Components (`ui/components`)
`ScreenColumn`, `ScreenHeader` (large title + eyebrow), `SectionHeader`, `SectionLabel`, `Module`
(grouped surface), `Hairline` (inset), `ListRow`, `EmptyState`, `PrimaryButton`, `SecondaryButton`
(tonal), `RitualIconButton` (tonal disc), `RitualSwitch`, `SegmentedChoice` (sliding thumb),
`RitualBottomBar` (glass), `CompletionCheck`, `ProgressRing`, `WeekDots`, `TimerDial`,
`ConfirmDialog`, `RitualMenu`, `RitualSnackbarHost`, `DelayedLoadingIndicator`, `glassSurface`.
