package app.ritualhabits.ui

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Outfit ships inside the APK (no runtime download) together with its licence. */
@RunWith(RobolectricTestRunner::class)
class BundledFontTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `Outfit is bundled as a font resource`() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.outfit_variable))
    }

    @Test fun `the Open Font License is shipped with the app`() {
        val licence = context.assets.open("licenses/Outfit-OFL.txt").bufferedReader().readText()
        assertTrue(licence.contains("SIL OPEN FONT LICENSE Version 1.1", ignoreCase = true))
        assertTrue(licence.contains("Outfit Project Authors"))
    }
}
