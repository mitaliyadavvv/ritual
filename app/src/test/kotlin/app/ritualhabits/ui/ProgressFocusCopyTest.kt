package app.ritualhabits.ui

import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.DayStatus
import app.ritualhabits.ui.focus.formatCountdown
import app.ritualhabits.ui.focus.spokenCountdown
import app.ritualhabits.ui.progress.dayStatusText
import app.ritualhabits.ui.progress.monthTitle
import app.ritualhabits.ui.progress.percent
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProgressFocusCopyTest {
    @Test fun `countdown is MM SS and rounds up`() {
        assertEquals("25:00", formatCountdown(25.minutes))
        assertEquals("60:00", formatCountdown(60.minutes))
        assertEquals("07:05", formatCountdown(7.minutes + 5.seconds))
        assertEquals("00:01", formatCountdown(200.milliseconds))
        assertEquals("00:00", formatCountdown(0.seconds))
        assertEquals("10 minutes remaining", spokenCountdown(10.minutes))
        assertEquals("1 minute 5 seconds remaining", spokenCountdown(65.seconds))
    }

    @Test fun `progress copy`() {
        assertEquals("October 2026", monthTitle(CalendarMonth(2026, 10), Locale.UK))
        assertEquals("—", percent(0, 0))
        assertEquals("67%", percent(2, 3))
        assertEquals("All 3 habits done", dayStatusText(DayStatus.COMPLETE, 3, 3))
        assertEquals("1 of 3 habits done", dayStatusText(DayStatus.PARTIAL, 3, 1))
        assertEquals("No habits were due", dayStatusText(DayStatus.NOTHING_DUE, 0, 0))
        assertEquals("Today · 1 of 2 done so far", dayStatusText(DayStatus.IN_PROGRESS, 2, 1))
    }
}
