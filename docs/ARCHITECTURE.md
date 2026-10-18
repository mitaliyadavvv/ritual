# Ritual — Architecture (Stage 2)

Status: proposed and partially implemented (domain + data layer + tests). No UI yet.

---

## 1. Identity

| Item | Value |
|---|---|
| App name | Ritual |
| Application ID | `app.ritualhabits` |
| Kotlin namespace | `app.ritualhabits` (app), `app.ritualhabits.core` (domain) |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Version | `versionCode 1`, `versionName 0.1.0` |

**Why `app.ritualhabits`:** neutral (no personal name), short, matches a plausible future domain
(`ritualhabits.app`), and does not collide with the old prototype (`com.habittracker.app`), so both can be
installed side by side during migration. The application ID is permanent once uploaded to Google Play.
Uniqueness can only be confirmed when the first build is uploaded in Play Console. If it is taken, the
fallback is `app.ritualhabits.android`. Decide before the first upload, never after.

---

## 2. Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3, fully re-themed to monochrome |
| State | `ViewModel` + `StateFlow` (UI state as immutable data classes) |
| Navigation | Navigation Compose (4 tabs + Settings route) |
| Persistence | Room (habits, eligibility periods, completions, focus sessions) |
| Preferences | Preferences DataStore (username, theme, haptics, permission flags) |
| Dates | `kotlinx-datetime` (`LocalDate`, `Instant`, `TimeZone`) |
| DI | Manual `AppContainer`. No Hilt: fewer moving parts for a 5-screen app |
| Background | `AlarmManager` alarm + `BroadcastReceiver` for focus end. **No foreground service** |
| Network | None. No `INTERNET` permission, no analytics, no backend |

### Modules

```
RitualNative/
├── core/   Pure Kotlin/JVM. No Android imports. Business rules only.
│   ├── time/    HabitDay, AppClock (SystemAppClock, ManualClock)
│   ├── model/   EligibilityPeriod, CompletionKey, FocusSession, ThemeMode, …
│   └── rules/   EligibilityRules, HabitHistory, StreakCalculator, FocusTimer, HabitValidation
└── app/    Android application.
    ├── data/local/       Room entities, DAOs, converters, RitualDatabase
    ├── data/repository/  Habit, Completion, Focus, Preferences repositories, LocalDataManager
    ├── data/day/         HabitDayTracker (midnight / resume / time-change refresh)
    ├── di/               AppContainer
    └── ui/               (Stage 3+) theme, components, screens, ViewModels
```

`core` depends only on `kotlinx-datetime`. Every rule that decides *what counts* (dates, eligibility,
streaks, timer transitions, validation) lives there and is unit-tested on the JVM. That is the part to
reuse for iOS later, either by porting it to Kotlin Multiplatform (the module is already KMP-friendly) or
by using it as the reference spec for a Swift port.

### Data flow

```
Room/DataStore ──Flow──▶ Repository ──Flow──▶ ViewModel (combine with HabitDayTracker.today)
                                                  │ maps through core rules (StreakCalculator…)
                                                  ▼
                                           StateFlow<UiState> ──▶ Compose screen
User intent ──▶ ViewModel ──suspend──▶ Repository (Room transaction, core rule validates) ──▶ DB
```

Screens never compute streaks or dates themselves. They render state derived from the database plus
the current habit day.

---

## 3. Data model (Room, schema v1)

### `habits`: identity and display data
| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID. Stable forever; never reused |
| `name` | TEXT | normalised (trimmed, single spaces), 1–60 chars |
| `description` | TEXT? | optional, ≤ 280 chars |
| `sort_order` | INTEGER | user ordering on Today / Manage |
| `created_at` | INTEGER | epoch ms (UTC instant) |
| `archived_at` | INTEGER? | set when archived |
| `deleted_at` | INTEGER? | soft delete; row is never physically removed in normal use |

Derived state: `deleted_at != null` → Deleted; else `archived_at != null` → Archived; else Active.

### `habit_periods`: **eligibility history** (the core of historical integrity)
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `habit_id` | TEXT FK → habits.id | indexed |
| `start_date` | TEXT `yyyy-MM-dd` | first habit day the habit is required |
| `end_date` | TEXT? | **exclusive**; `null` = still required |

A habit is *required on day D* if and only if it has a period with `start_date ≤ D < end_date` (or open-ended).

### `completions`: one row per habit per habit day
| Column | Type | Notes |
|---|---|---|
| `habit_id` | TEXT FK | **composite PK (`habit_id`, `date`)**, so duplicates are impossible |
| `date` | TEXT `yyyy-MM-dd` | the habit day it counts for; indexed |
| `completed_at` | INTEGER | instant it was recorded |
| `source` | TEXT | `MANUAL` or `FOCUS` |
| `focus_session_id` | TEXT? | set when created by a finished focus session |

### `focus_sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `habit_id` | TEXT FK | indexed |
| `planned_duration_ms` | INTEGER | |
| `status` | TEXT | `RUNNING` · `PAUSED` · `COMPLETED` · `CANCELLED`; indexed |
| `started_at` | INTEGER | |
| `ends_at` | INTEGER? | wall-clock end; set while `RUNNING` (kept after completion) |
| `remaining_ms` | INTEGER? | set while `PAUSED` |
| `finished_at` | INTEGER? | completion = `ends_at`; cancel = cancel time |
| `completion_date` | TEXT? | habit day the session completed on |
| `acknowledged` | INTEGER (bool) | user has dismissed the "Session complete" state |

### Relationships
```
habits 1 ──< habit_periods
habits 1 ──< completions
habits 1 ──< focus_sessions
focus_sessions 0..1 ──< completions (via focus_session_id, informational)
```
Foreign keys use `ON DELETE CASCADE`. That only matters for *Clear all local data*, because normal
deletes are soft.

### Storage of dates and times
- **Instants** (`created_at`, `ends_at`, …) are stored as epoch milliseconds (UTC). They are time-zone independent.
- **Habit days** (`start_date`, `date`, …) are stored as ISO `yyyy-MM-dd` strings. A habit day is a *label*,
  computed once in the device's zone at the moment of the action, and **never recomputed** afterwards.
  ISO strings sort correctly and are easy to inspect.

### Preferences (DataStore, not Room)
`user_name` (default empty), `theme_mode` (`SYSTEM` default · `LIGHT` · `DARK`), `haptics_enabled`
(default true), `notification_permission_requested` (bool).

---

## 4. Habit day and midnight rollover

**Definition.** The habit day is the calendar date of *now* in the device's *current* time zone. It changes at
local 00:00. There is no offset (`HabitDay.of(instant, zone)` in `core`).

**How "today" is kept current.** `HabitDayTracker` exposes `StateFlow<LocalDate>` and recomputes it:
1. on every app foreground (`ProcessLifecycleOwner` `ON_START`), which covers "opened or resumed after midnight";
2. on `ACTION_DATE_CHANGED`, `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` (receiver registered only
   while the app is in the foreground);
3. via a coroutine that sleeps until the next boundary (`HabitDay.nextBoundary`) while the app is visible.

No exact-time background job is needed: nothing is *written* at midnight. A new day simply has no
completion rows yet, so the checklist naturally appears empty, while yesterday's rows are untouched.
ViewModels `combine` the day flow with database flows, so every screen refreshes automatically.

**Write guard.** `CompletionRepository.toggle(habitId, date)` rejects the write (`StaleDate`) if `date` is
not the current habit day. A screen left open across midnight can never write into the wrong day. The
UI then re-renders on the new day.

**DST.** Boundaries use `LocalDate.atStartOfDayIn(zone)`, which returns the first valid instant when
midnight does not exist (zones that shift at 00:00). The date itself is never ambiguous.

**Time-zone changes.** Stored rows are never rewritten. Example: a completion recorded at 00:30 in
Mumbai is stored as `2026-10-19`. If you then fly to Los Angeles, where it is still the 18th, the app
shows the 18th as today. The 19th's row stays in place and counts when LA reaches the 19th.
Travelling east can skip a calendar day, which then has no completions. V1 does not compensate for
this (no grace days, per brief).

**Manual clock changes** are out of scope for integrity: anyone who sets their clock back can produce
records for earlier dates. The rules below ensure *normal* actions never alter the past. They do not
try to defend against deliberate clock manipulation.

---

## 5. Eligibility and historical integrity

The prototype recomputed past days from the *current* habit list, so deleting a habit could turn an
old failed day into a successful one. Ritual instead records **when each habit was required**, and
every past-day result is a pure function of append-only facts.

### Rules (implemented in `core/rules/EligibilityRules.kt`)
| Action on habit day `T` | Effect on `habit_periods` |
|---|---|
| Create habit | insert period `[T, ∞)`, so it is **required from its creation day** |
| Archive / delete | close the open period at `T` (`end_date = T`): no longer required *from today*. If the period started on `T` (created and removed the same day), the empty period is deleted |
| Restore / undo delete | if a period ended exactly at `T` (or later), reopen it, so there is no gap; otherwise insert `[T, ∞)` |
| Undo delete | returns the habit to its prior state: an archived habit stays archived (no period change); an active one is reactivated as above |
| Reorder | `sort_order` only, rewritten atomically for the active list. Never touches periods or completions |
| Rename / edit description / reorder | **no change.** History is keyed by `habit_id`, not name |

### Why history cannot be rewritten
- Every period change affects only days `≥ T` (today or later). Days `< T` keep exactly the requirement set they had.
- Completions can only be written for **today** (manual toggle), or by a focus session for the day it ended.
- Completions are only accepted for habits **eligible on that date**. An archived or deleted habit cannot gain new completions.
- Nothing is hard-deleted except via *Clear all local data*.

### Same-day guarantees (verified in Stage 4b)
- Archive then restore on the same day restores the *identical* period list, so today's completion counts again.
- Create, archive and restore on the same day: the empty period is removed, then `[T, ∞)` is re-inserted.
- `HistoryIntegrityPropertyTest` replays 300 random 40-day lifecycles (create, archive, delete, restore,
  complete; several actions per day) and asserts that no action on day `T` changes any result for a day
  before `T`, and that no habit ever has overlapping or multiple open periods. A deliberately broken rule
  makes it fail.

**Today is provisional.** Until midnight, archiving or adding a habit can change *today's* outcome (adding
a habit at 23:00 means today needs it too, as you decided). Once the day passes it is fixed.

No separate "daily snapshot" table is needed. Snapshots duplicate truth and can drift. Periods plus
completions *are* the history, and results are recalculated deterministically every time.

---

## 6. Streak and analytics rules (implemented in `core/rules/StreakCalculator.kt`)

For each habit day `D` up to today:
- `required(D)` = habits with a period covering `D`
- `completed(D)` = required habits with a completion row for `D` (other rows are ignored)
- **`D` qualifies** ⇔ `required(D)` is non-empty **and** `completed(D) == required(D)`

| Rule | Definition |
|---|---|
| Current streak | If today qualifies, count back from today. Otherwise count back from yesterday (today is still pending and does not break the streak). Stop at the first non-qualifying day |
| Day with no required habits | does **not** qualify, so it **breaks** the streak (no free days) |
| Longest streak | longest run of consecutive qualifying days from the first tracked day to today |
| Future-dated rows | ignored until that date becomes today |
| Duplicates | impossible (composite PK); the calculator also works on sets |
| Completion rate (range) | completed required slots ÷ required slots, days `from..to` inclusive. Today's slots count, so the rate updates live. `null` when nothing was required |
| Per-habit streak | consecutive **eligible** days completed. Days the habit was archived are skipped (paused, not broken). Today, if pending, does not break it |

No freezes, grace days or other gamification.

---

## 7. Focus timer lifecycle (implemented in `core/rules/FocusTimer.kt`, `FocusRepository`)

```
            start                 pause                resume
   (none) ───────▶ RUNNING ───────────▶ PAUSED ───────────▶ RUNNING
                     │  \                  │
     now ≥ ends_at   │   \ cancel          │ cancel
   (reconcile)       ▼    ▼                ▼
                COMPLETED  CANCELLED ◀─────┘
```

- **Start:** `ends_at = now + duration`. Only one RUNNING/PAUSED session can exist. The habit must be eligible today.
  Presets: 15, 25, 45, 60 minutes.
- **Remaining time** is always `ends_at − now`, never a decrementing counter. It survives backgrounding,
  screen lock, process death and reboot because `ends_at` is in Room.
- **Pause** stores `remaining_ms` and clears `ends_at`. **Resume** sets `ends_at = now + remaining`.
- **Reconcile** runs on app start, every foreground, before every timer action, from the alarm receiver,
  and after boot. For each RUNNING session with `ends_at ≤ now`, in one transaction:
  1. `UPDATE … SET status = COMPLETED … WHERE id = ? AND status = 'RUNNING'`. Only the caller that changes
     a row continues, so concurrent callers (UI and alarm) cannot both complete it.
  2. `completion_date = HabitDay.of(ends_at, zone)`: the day the session *ended*, not the day it was reconciled.
     A session that ends at 00:10 completes the new day. One that ended at 23:55 completes the old day, even if
     the phone is only opened the next morning.
  3. `INSERT OR IGNORE` a completion row (`source = FOCUS`) only if the habit is eligible that day. If the habit
     was already completed manually, the existing row is kept and nothing is inflated.
- **Cancel** never writes a completion. Cancelling after `ends_at` has passed is treated as completion,
  because reconcile runs first.
- **Archive/delete** of a habit cancels its active session.
- The completed state stays visible until the user dismisses it (`acknowledged = 1`).
- Wall-clock limitation: changing the device clock mid-session shifts the end. Accepted for V1; this is
  the only representation that also survives reboot.

---

## 8. Notifications and alarms (implemented in Stage 6)

| Situation | Behaviour |
|---|---|
| Session running | `AlarmManager` alarm at `ends_at` (`FocusAlarms`). Exact (`setExactAndAllowWhileIdle`) if the user allowed exact alarms, otherwise `setAndAllowWhileIdle` (Android 14+ default; delivery may be several minutes late) |
| Alarm fires, app in background | `FocusAlarmReceiver` → `FocusCompletionHandler.onAlarm()` reconciles (exactly once) and posts "Focus session complete" naming the habit and the outcome. Tapping it opens Ritual on the Focus tab |
| Alarm fires, app in foreground | Reconciles; no notification (the Focus screen shows the result and plays the confirm haptic) |
| Pause / cancel / completion seen by the screen | Alarm cancelled. Resume re-arms it with the new end time |
| Returning to Focus | Reconcile, then re-arm a running session's alarm (so it becomes exact if precise alerts were just allowed) |
| Reboot or app update | `FocusBootReceiver` reconciles, then re-arms running sessions |

**Why no foreground service:** correctness never depends on code running at the end time. Every
entry point reconciles from the stored `ends_at`. The alarm only *tells* the user.

**Permissions:**
| Permission | Use | Handling |
|---|---|---|
| `POST_NOTIFICATIONS` | completion notification | asked once, on the first Start, after a short in-app explanation ("Continue" / "Not now"). If denied, the timer works fully and Focus shows a quiet note with a link to settings |
| `SCHEDULE_EXACT_ALARM` | on-time alert | never prompted automatically. When not allowed, Focus shows "may arrive a few minutes late · Allow precise alerts", which opens the system page |
| `RECEIVE_BOOT_COMPLETED` | re-arm after reboot | normal permission |

Not used: `INTERNET`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE*`, `WAKE_LOCK`. Not built: the ongoing
countdown notification sketched in Stage 2 (the system's notification shows only the end).

## 8a. Progress (implemented in Stage 6)
`core/rules/ProgressCalculator` builds the month and habit views from `HabitHistory`, the same
eligibility periods, completions and "all due habits done" rule used for streaks.
- Day: COMPLETE (= qualifies for the streak), PARTIAL (finished day, some done), MISSED (finished
  day, none done), IN_PROGRESS (today, not complete; never "missed"), NOTHING_DUE (no habit was
  due; neither success nor miss), FUTURE.
- Habit day: DONE, MISSED, PENDING (today), NOT_DUE (before creation, archived gap, after deletion;
  never a miss), FUTURE.
- Monthly completion rate = done slots ÷ due slots over days that have occurred (today included).
  Complete days count days ≤ today; partial days count finished days only.
- Archived and deleted habits stay in the month they were due, with their history, labelled.
- Navigation runs from the first tracked month to the current month. No schema change was needed.

## 8b. Habit reminders (implemented after Stage 6; schema v2)
- **Storage:** `habits.reminder_enabled` (INTEGER NOT NULL DEFAULT 0) and `habits.reminder_time`
  (minutes after local midnight, nullable). `MIGRATION_1_2` only adds the two columns, so existing
  habits migrate with reminders off and every other row is untouched (`ReminderMigrationTest`). The
  time is kept when the switch is turned off, so it's remembered.
- **Editor:** a Reminder block in the shared Add/Edit sheet. Off by default, 9:00 AM when first
  switched on, Material 3 time picker, saved only with the habit. Switching it on is when Ritual asks
  for notification permission (Android 13+).
- **Scheduling:** `ReminderCoordinator` observes the habits table and keeps exactly one AlarmManager
  alarm per active habit with its reminder on, at `ReminderSchedule.next(time)` (the next local
  occurrence; DST gaps move forward, never skip). Create, edit, archive, restore, delete and
  undo-delete all go through the existing repository; the coordinator reacts to the resulting
  rows, so lifecycle code is unchanged. Alarm identity is `ritual://reminder/<habitId>`.
- **Delivery:** `HabitReminderReceiver` → `onReminder`: arms tomorrow's alarm first, then shows
  "A little nudge from Ritual · Time for <habit>" only if the habit is still active, due today
  (eligibility periods) and not done today, and the delivery is for today (no backlog from earlier
  days). Notifications are tagged per habit, so repeats replace and never stack. A reminder never
  writes a completion. Tapping opens Today.
- **System events:** `ReminderResyncReceiver` recomputes every alarm on boot, app update, clock
  change and time-zone change. The first sync after app start is a full resync too.
- **Timing:** exact only if the user has already allowed exact alarms (Focus's "Allow precise
  alerts"); otherwise `setAndAllowWhileIdle`, which may arrive a few minutes after the set time.
  Reminders never request exact-alarm access.

## 9. Validation

- Name: trimmed, internal whitespace collapsed, 1–60 characters, **unique case-insensitively** among
  active and archived habits (deleted habits free their name).
- Description: optional, trimmed, ≤ 280 characters, blank → `null`.
- Restoring a habit whose name has since been reused is rejected with the duplicate error, so the user renames first.

---

## 10. Settings and data deletion

- No accounts exist, so Settings offers **"Delete all local data"** (not "delete account"). It is confirmed
  explicitly, then wipes all Room tables and DataStore, cancels alarms and clears notifications. The copy
  states that Ritual stores data only on this device.
- Android Auto Backup is left on (system-managed, encrypted, no Ritual server). Exclusion rules are
  reviewed in Stage 6.

---

## 11. Migration strategy

- **Room:** `exportSchema = true` to `app/schemas/` (committed to version control). Current
  version: 2 (`MIGRATION_1_2`: reminder columns). Every schema change
  bumps the version and ships an explicit `Migration` (or `AutoMigration`) plus a `MigrationTestHelper` test.
  `fallbackToDestructiveMigration` is **never** enabled.
- **DataStore:** keys are additive with defaults. Renames require a `DataMigration`.
- **Legacy prototype (Capacitor, `com.habittracker.app`):** different app ID and WebView storage, so no
  automatic migration. Optional later feature: import the prototype's JSON export (format `2.0.0`):
  habit `id` and `name` preserved, `completions` map to rows, the period starts at the local date of `createdAt`,
  `dailyHistory` is ignored (derived), and archived habits get a closed period ending on the import day.
  Best-effort only: the prototype has no record of when habits were archived or deleted.

---

## 12. Tests

`core` (JVM, fast):
- `HabitDayTest`: 23:59:59 vs 00:00, the same instant in two zones, DST-gap midnight, next boundary.
- `EligibilityRulesTest`: create, archive, archive same day, restore same day (merge), restore later (new period), restore when already open.
- `StreakCalculatorTest`: all-required rule; partial day fails; empty day breaks; today pending; longest;
  new habit doesn't change past; archive/delete doesn't change past; future rows ignored; rate; per-habit
  streak skips archived gaps; ported prototype cases.
- `FocusTimerTest`: remaining from timestamp; pause/resume; due detection; completion date across midnight;
  invalid transitions rejected.
- `HabitValidationTest`: empty, whitespace, too long, duplicate (case-insensitive), self-rename allowed.

`app` (Robolectric + in-memory/file Room, DataStore on a temp file):
- toggle complete/uncomplete; no duplicates; stale date rejected; archived habit not completable.
- create/archive/delete/restore keep past streak results identical.
- focus: double reconcile → one completion; cancel → none; already complete → still one row; end after
  midnight → next day; archive cancels session.
- persistence across closing and reopening the database file.
- time-zone switch leaves stored rows unchanged.
- preferences defaults and persistence.

Run: `./gradlew test` (see README).
