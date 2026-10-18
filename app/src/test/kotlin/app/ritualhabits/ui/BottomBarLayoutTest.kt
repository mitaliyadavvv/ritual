package app.ritualhabits.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.components.tabLayout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomBarLayoutTest {
    private fun List<Dp>.sum() = fold(0.dp) { acc, w -> acc + w }

    @Test fun `equal tabs and all labels when they fit`() {
        val layout = tabLayout(inner = 352.dp, labelWidths = listOf(40.dp, 56.dp, 40.dp, 54.dp), selectedIndex = 1)
        assertTrue(layout.showAllLabels)
        assertEquals(List(4) { 88.dp }, layout.widths)
    }

    @Test fun `large text widens the selected tab so its label is never clipped`() {
        // 200% text: "Progress" ~110 dp, more than an equal quarter of the bar.
        val labels = listOf(80.dp, 110.dp, 70.dp, 104.dp)
        for (selected in labels.indices) {
            val layout = tabLayout(inner = 352.dp, labelWidths = labels, selectedIndex = selected)
            assertEquals(false, layout.showAllLabels)
            assertTrue(layout.widths[selected] >= labels[selected] + 8.dp, "selected tab $selected too narrow")
            layout.widths.forEachIndexed { i, w -> if (i != selected) assertTrue(w >= 52.dp, "icon tab $i below 52 dp") }
            assertEquals(352f, layout.widths.sum().value, 0.01f)
        }
    }
}
