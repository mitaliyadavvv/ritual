package app.ritualhabits.ui

import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.dp
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.ui.navigation.TopLevelDestination
import app.ritualhabits.ui.navigation.Routes
import app.ritualhabits.ui.theme.DarkColors
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.LayoutMetrics
import app.ritualhabits.ui.theme.LightColors
import app.ritualhabits.ui.theme.RitualColors
import app.ritualhabits.ui.theme.RitualHaptics
import app.ritualhabits.ui.theme.isDark
import app.ritualhabits.ui.today.TodayHabit
import app.ritualhabits.ui.today.TodayUiState
import app.ritualhabits.ui.today.formatHabitDay
import app.ritualhabits.ui.today.todaySubtitle
import app.ritualhabits.ui.today.todayTitle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignSystemTest {

    @Test fun `theme mode resolves against the system setting`() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemIsDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemIsDark = false))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemIsDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemIsDark = false))
    }

    @Test fun `palettes are strictly neutral grey`() {
        listOf(LightColors, DarkColors).flatMap { it.all() }.forEach { c ->
            assertTrue(c.red == c.green && c.green == c.blue, "Non-neutral colour $c")
        }
    }

    @Test fun `text meets WCAG contrast on every background`() {
        for (p in listOf(LightColors, DarkColors)) {
            for (bg in listOf(p.background, p.surface, p.surfaceRaised, p.fill)) {
                assertTrue(contrast(p.textPrimary, bg) >= 7.0, "primary on $bg")
                assertTrue(contrast(p.textSecondary, bg) >= 4.5, "secondary ${contrast(p.textSecondary, bg)} on $bg")
            }
            assertTrue(contrast(p.onAccent, p.accent) >= 7.0)
            assertTrue(contrast(p.onAccentSecondary, p.accent) >= 4.5, "inverted secondary ${contrast(p.onAccentSecondary, p.accent)}")
        }
    }

    @Test fun `tab labels on glass stay readable over worst-case content`() {
        // Glass over the most contrasting content possible: black text behind light glass, white behind dark.
        for (p in listOf(LightColors, DarkColors)) {
            val behind = if (p.isDark) Color.White else Color.Black
            val effective = p.glass.compositeOver(behind)
            assertTrue(contrast(p.textPrimary, effective) >= 7.0, "primary on glass ${contrast(p.textPrimary, effective)}")
            assertTrue(contrast(p.textSecondary, effective) >= 4.5, "secondary on glass ${contrast(p.textSecondary, effective)}")
        }
    }

    @Test fun `haptics are silent when disabled`() {
        val fired = mutableListOf<Int>()
        RitualHaptics(enabled = false, performer = { fired += it }).perform(HapticEvent.Confirm)
        assertEquals(emptyList(), fired)
        RitualHaptics(enabled = true, performer = { fired += it }, sdkInt = 34).perform(HapticEvent.Confirm)
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM), fired)
    }

    @Test fun `haptics fall back on older Android versions`() {
        val old = RitualHaptics(enabled = true, performer = { }, sdkInt = 28)
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, old.constantFor(HapticEvent.Confirm))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, old.constantFor(HapticEvent.Selection))
        val new = RitualHaptics(enabled = true, performer = { }, sdkInt = 34)
        assertEquals(HapticFeedbackConstants.TOGGLE_ON, new.constantFor(HapticEvent.ToggleOn))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, new.constantFor(HapticEvent.Selection))
    }

    @Test fun `layout breakpoints`() {
        assertEquals(16.dp, LayoutMetrics.forWidth(340.dp).gutter)
        assertEquals(20.dp, LayoutMetrics.forWidth(411.dp).gutter)
        assertEquals(411.dp, LayoutMetrics.forWidth(411.dp).contentMaxWidth)
        assertEquals(600.dp, LayoutMetrics.forWidth(700.dp).contentMaxWidth)
        assertEquals(680.dp, LayoutMetrics.forWidth(1200.dp).contentMaxWidth)
        assertEquals(440.dp, LayoutMetrics.forWidth(1200.dp).bottomBarMaxWidth)
    }

    @Test fun `routes map to tabs and settings hides the bar`() {
        assertEquals(TopLevelDestination.FOCUS, TopLevelDestination.fromRoute("focus"))
        assertNull(TopLevelDestination.fromRoute(Routes.SETTINGS))
        assertNull(TopLevelDestination.fromRoute(null))
        assertEquals(listOf("Today", "Progress", "Focus", "Manage"), TopLevelDestination.entries.map { it.label })
    }

    @Test fun `today summary copy follows the state`() {
        fun ready(done: Int, total: Int, streak: Int = 0) = TodayUiState.Ready(
            date = LocalDate(2026, 10, 18),
            habits = List(total) { TodayHabit("h$it", "Habit $it", null, completed = it < done) },
            hasActiveHabits = true,
            currentStreak = streak,
        )
        assertEquals("3 habits today", todayTitle(ready(0, 3)))
        assertEquals("1 habit today", todayTitle(ready(0, 1)))
        assertEquals("2 left today", todayTitle(ready(1, 3)))
        assertEquals("All done for today", todayTitle(ready(3, 3)))
        assertEquals("Finish all to start a streak", todaySubtitle(ready(1, 3)))
        assertEquals("4-day streak · finish all to continue", todaySubtitle(ready(1, 3, streak = 4)))
        assertEquals("5-day streak", todaySubtitle(ready(3, 3, streak = 5)))
        assertEquals("Today counts toward your streak", todaySubtitle(ready(3, 3, streak = 0)))
    }

    @Test fun `habit day header format`() {
        assertEquals("Sunday, 18 October", formatHabitDay(LocalDate(2026, 10, 18), Locale.UK))
    }

    private fun RitualColors.all() =
        listOf(background, surface, surfaceRaised, fill, glass, glassEdge, textPrimary, textSecondary, textTertiary, border, borderStrong, accent, onAccent, onAccentSecondary, scrim, shadow)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
