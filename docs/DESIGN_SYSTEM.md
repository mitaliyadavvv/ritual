# Ritual — Design System (Stage 3)

Source of truth: `app/src/main/kotlin/app/ritualhabits/ui/theme/` and `ui/components/`.
Access tokens in composables through `Ritual.colors`, `Ritual.motion`, `Ritual.haptics`, `Ritual.layout`.

## Colour
Strictly neutral greys (R = G = B), enforced by `DesignSystemTest`.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #FAFAFA | #0A0A0A | screen |
| surface | #FFFFFF | #141414 | modules, cards |
| surfaceRaised | #F2F2F2 | #1F1F1F | segmented track, selected pill |
| glass | white 92 % | #141414 90 % | floating bottom bar only |
| textPrimary | #0A0A0A | #F5F5F5 | titles, body (≥ 7:1) |
| textSecondary | #616161 | #A3A3A3 | supporting text (≥ 4.5:1, tested) |
| textTertiary | #8F8F8F | #6E6E6E | disabled / decorative only |
| border / borderStrong | #E5E5E5 / #C7C7C7 | #262626 / #3D3D3D | hairlines |
| accent / onAccent | black / near-white | near-white / black | filled controls |

Every Material 3 colour role is mapped to these tokens, including error, so stock components cannot
introduce colour. State is never conveyed by colour alone: use text, icons, fill vs outline, and weight.

Themes: System (default), Light and Dark, stored in DataStore. Status and navigation bar icons follow
the *in-app* theme. The splash screen is held until the stored theme is loaded, so the wrong theme
never flashes.

## Typography
System sans-serif (Roboto, or Google Sans on recent Pixels): offline and familiar. To bundle a
typeface later, change `Sans` in `Type.kt`.

| Style | Size / weight | Use |
|---|---|---|
| display | 72 Light, tabular | focus countdown |
| title | 30 SemiBold | screen titles |
| heading | 17 SemiBold | sections, sheets |
| value | 28 Medium, tabular | analytics numbers |
| body | 16 Regular | rows |
| bodySmall | 14 Regular | supporting text |
| label | 14 Medium | buttons |
| overline | 12 Medium, +8 % tracking, uppercase | dates, section labels |
| caption | 12 Regular, tabular | tab labels, chart axes |

All sizes are in `sp` and scale with the user's font size. Exception: bottom-bar labels cap at 1.3×
(as the system navigation bar does) so four labels stay on one line.

## Space, shape, size
- Spacing: 2 · 4 · 8 · 12 · 16 · 24 · 32 · 48 dp (`Spacing`).
- Radii: 8 · 14 · 20 · 28 dp (`Radii`). Modules use 14, the bottom bar 28.
- Borders: 1 dp hairline. No elevation shadows.
- Touch targets: ≥ 48 dp (`Sizes.minTouchTarget`). Rows ≥ 56 dp.
- Responsive (`LayoutMetrics`): < 600 dp → 20 dp gutter, full width. 600–839 → 32 dp gutter, 600 dp
  content column, 440 dp bar. ≥ 840 → 40 dp gutter, 680 dp column. Content is centred.

## Surfaces and translucency
Surfaces are solid. The **only** translucent element is the floating bottom bar: ≥ 90 % opaque fill
plus hairline border, no blur, so labels keep full contrast over scrolled content.

## Motion
| Spec | Duration | Use |
|---|---|---|
| quick | 120 ms | press scale (0.97), toggles, colour changes |
| standard | 220 ms | tab cross-fade, expand/collapse, bar show/hide |
| emphasized | 320 ms | pushed screens (Settings slides 1/6 width + fade) |

Easing is cubic (0.2, 0, 0, 1), and exits are faster than entries. If the system "Remove animations"
setting is on, every spec snaps and navigation transitions are disabled. Predictive back is enabled.

## Haptics
Semantic events (`HapticEvent`): Selection, ToggleOn, ToggleOff, Confirm, Reject. They map to
`HapticFeedbackConstants`, using API 34 constants with fallbacks for 26–33. They are delivered via
`View.performHapticFeedback`, which respects system settings, and are suppressed when Ritual's
own Haptics preference is off. No `VIBRATE` permission is needed.

## Components (`ui/components`)
`ScreenColumn`, `ScreenHeader`, `SectionLabel`, `Module`, `ListRow`, `Hairline`, `EmptyState`,
`PrimaryButton`, `SecondaryButton`, `RitualIconButton` (contentDescription required), `RitualSwitch`,
`SegmentedChoice` (radio semantics), `RitualBottomBar` (tab semantics), `RitualIcons` (24 dp line set).

## Navigation
Four tabs (Today, Progress, Focus, Manage) with saved state per tab. Settings is reached from the
sliders icon in every tab header and is pushed on top, with the bar hidden and a labelled Back button.
