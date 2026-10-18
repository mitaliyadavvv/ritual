package app.ritualhabits.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.data.focus.AndroidFocusAlarms
import app.ritualhabits.data.focus.AndroidFocusNotifier
import app.ritualhabits.data.repository.FocusOutcome
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The real AlarmManager / NotificationManager integrations, through Robolectric's shadows. */
@RunWith(RobolectricTestRunner::class)
class FocusAndroidTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.parse("2026-10-18T03:30:00Z")

    @Test fun `alarm is armed at the session end time and cancelled on request`() {
        val alarms = AndroidFocusAlarms(app)
        val shadow = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val session = FocusTimer.start("s", "h", 25.minutes, t0)
        alarms.schedule(session)
        assertEquals((t0 + 25.minutes).toEpochMilliseconds(), shadow.nextScheduledAlarm!!.triggerAtTime)
        alarms.schedule(session) // re-arming replaces rather than duplicates
        assertEquals(1, shadow.scheduledAlarms.size)
        alarms.cancel()
        assertNull(shadow.nextScheduledAlarm)
    }

    @Test fun `paused sessions are never armed`() {
        val alarms = AndroidFocusAlarms(app)
        val shadow = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val paused = FocusTimer.pause(FocusTimer.start("s", "h", 25.minutes, t0), t0 + 1.minutes)
        assertEquals(FocusStatus.PAUSED, paused.status)
        alarms.schedule(paused)
        assertNull(shadow.nextScheduledAlarm)
    }

    @Test fun `completion notification names the habit and opens Focus`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidFocusNotifier(app).apply { createChannel() }
        notifier.notifyEnded("Read 20 pages", FocusOutcome.MARKED_DONE)
        val manager = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val posted = manager.allNotifications.single()
        assertEquals("Focus session complete", posted.extras.getString("android.title"))
        assertTrue(posted.extras.getCharSequence("android.text").toString().contains("Read 20 pages"))
        assertTrue(posted.contentIntent != null)
    }

    @Test fun `no notification without permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidFocusNotifier(app).apply { createChannel() }.notifyEnded("Read", FocusOutcome.MARKED_DONE)
        val manager = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        assertTrue(manager.allNotifications.isEmpty())
    }
}
