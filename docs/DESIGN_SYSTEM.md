# Ritual — Design System (Stage 5a: editorial redesign)

Direction: **editorial, graphic, playful — strictly monochrome.** Minimal in clutter, not in personality.
Source of truth: `app/src/main/kotlin/app/ritualhabits/ui/theme/` and `ui/components/`.
Access tokens in composables through `Ritual.colors`, `Ritual.motion`, `Ritual.haptics`, `Ritual.layout`.

Status: tokens and shared components are redesigned; **Today is the reference screen**. Manage and
Settings inherit the new tokens but their layouts are redesigned in the next stage.

## Typeface
**Outfit**, bundled as one variable font: `res/font/outfit_variable.ttf` (Google Fonts, SIL OFL 1.1).
The licence ships in the APK at `assets/licenses/Outfit-OFL.txt`, with source and checksum in
`assets/licenses/README.txt`. There are no runtime font downloads. Weights are instances of the
`wght` axis; **nothing below Regular (400)** is used. Numerals use tabular figures (`tnum`).

| Style | Size / weight | Use |
|---|---|---|
| hero | 152 Black, −5 % tracking | today's count (fits to space) |
| numeral | 72 ExtraBold | streak length, outlined totals |
| display | 88 SemiBold | focus countdown |
| title | 44 Bold, −3.5 % | screen titles, statements ("Day complete.") |
| heading | 24 SemiBold | sheet titles |
| value | 32 Bold | analytics values |
| rowTitle | 20 Medium | habit names |
| body / bodySmall | 18 / 15 Regular | copy |
| label | 16 SemiBold | buttons |
| overline | 12 SemiBold, +14 % tracking, uppercase | dates, section markers ("01 HABITS") |
| caption | 13 Regular | tab labels, week-strip letters |

**Accessibility:** all text is in `sp` and follows the system font size, with **no caps**. Only
decorative numerals (`FitText`) shrink to fit their space, never below a readable minimum. Layouts
adapt instead of clipping: the statement and streak stack, the row index column widens, and the
bottom bar shows only the selected label when labels no longer fit (every tab keeps its name for TalkBack).

## Colour
Strictly neutral (R = G = B), enforced with contrast in `DesignSystemTest`.

| Token | Light | Dark | Use |
|---|---|---|---|
| background | #F3F3F3 | #0B0B0B | paper |
| surface / surfaceRaised | #FFFFFF / #E6E6E6 | #161616 / #242424 | sheets, tracks |
| textPrimary (ink) | #0A0A0A | #F3F3F3 | type, rules, marks |
| textSecondary | #575757 | #A8A8A8 | supporting text (≥ 4.5:1) |
| accent / onAccent | ink / paper | paper / ink | inverted blocks, filled controls |
| onAccentSecondary | #A8A8A8 | #575757 | secondary text on inverted blocks (≥ 4.5:1) |

Intensity comes from **inversion** (solid ink slabs on paper), scale and weight, never from colour.

## Composition
- One dominant element per screen (Today: the count).
- Heavy 2 dp rules open sections. Hairlines separate rows. Lists are indexes, not cards.
- Numbered section markers ("01 HABITS") and index numbers (hidden from TalkBack).
- Asymmetry and bleed: the streak slab runs off the right edge of the screen.
- Gutters follow `LayoutMetrics` (20 / 32 / 40 dp). Content is centred with a max width on tablets.

## Graphic progress
- **SegmentedProgress**: one block per habit, outlined when pending and filled when done. When all are
  done the gaps close into one solid slab.
- **Week strip** (inside the streak slab): the last 7 days. Filled = complete, outlined = missed,
  outlined with dot = today pending, dash = nothing was due. Built from `StreakCalculator.days`.
- **CompletionCheck**: a 40 dp heavy ring that fills, draws its tick and lands with a small overshoot.

## Shape and size
Radii 10 · 16 · 24 · 32 dp, plus full pills for buttons and segmented controls. Touch targets are
≥ 48 dp. Habit rows are ≥ 80 dp. Primary buttons are 56 dp pills. Outlines are 2 dp.

## Motion
quick 120 ms · standard 220 ms · emphasized 320 ms · **playful** spring (damping 0.55), at most one
playful motion per interaction (stamp, count roll, segment fill). Every spec snaps when the system
"Remove animations" setting is on.

## Haptics
Unchanged: semantic events (Selection, ToggleOn/Off, Confirm, Reject), gated by Ritual's preference.

## Navigation
A floating **inverted bar** (ink on light, paper on dark). The selected tab is a block of the page
colour. Its height grows with font size, and screens reserve its measured height.
