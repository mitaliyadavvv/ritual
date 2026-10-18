package app.ritualhabits.ui

import android.app.Application
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.SystemHaptics
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real Vibrator path, through Robolectric's vibrator shadow. */
@RunWith(RobolectricTestRunner::class)
class HapticsAndroidTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val vibrator: Vibrator get() = app.getSystemService(VibratorManager::class.java).defaultVibrator

    @Test fun `plays a short predefined effect tagged as touch feedback`() {
        Settings.System.putInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        SystemHaptics(app).perform(HapticEvent.ToggleOn)
        val shadow = shadowOf(vibrator)
        assertTrue(shadow.isVibrating)
        assertEquals(VibrationEffect.EFFECT_CLICK, shadow.effectId)
        assertEquals(VibrationAttributes.USAGE_TOUCH, (shadow.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
    }

    @Test fun `respects the system touch feedback switch`() {
        Settings.System.putInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        SystemHaptics(app).perform(HapticEvent.Confirm)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test fun `does nothing without a vibrator`() {
        shadowOf(vibrator).setHasVibrator(false)
        SystemHaptics(app).perform(HapticEvent.Confirm)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Config(sdk = [28])
    @Test fun `older Android gets a brief one-shot pulse`() {
        @Suppress("DEPRECATION")
        val old = app.getSystemService(Vibrator::class.java)
        SystemHaptics(app, sdkInt = 28).perform(HapticEvent.Selection)
        assertTrue(shadowOf(old).isVibrating)
        assertTrue(shadowOf(old).milliseconds in 1..30)
    }
}
