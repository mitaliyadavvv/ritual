package app.ritualhabits.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import app.ritualhabits.ui.navigation.RitualAppRoot
import app.ritualhabits.ui.theme.RitualTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** End-to-end through the real app shell and Room: empty state → add → complete → uncomplete. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayJourneyTest {
    @get:Rule val compose = createComposeRule()

    /** Room delivers on background threads; wait generously and dump the tree if it never arrives. */
    private fun awaitNode(matcher: SemanticsMatcher, unmerged: Boolean = false) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(matcher, unmerged).fetchSemanticsNodes().size == 1 }
        } catch (e: Throwable) {
            val roots = compose.onAllNodes(isRoot())
            val dump = roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString(maxDepth = 40) }
            throw AssertionError("Never found ${matcher.description}\n$dump", e)
        }
    }

    @Test fun `first habit can be added, completed and uncompleted`() {
        compose.setContent { RitualTheme { RitualAppRoot() } }

        awaitNode(hasText("Add your first habit"))
        compose.onNodeWithText("Add your first habit").performClick()

        val nameField = hasSetTextAction() and hasText("Name")
        awaitNode(nameField)
        compose.onNode(nameField).performTextInput("Drink water")
        // The sheet lives in its own dialog window, where Robolectric's touch injection is
        // unreliable; invoke the button's real onClick through its semantics action instead.
        compose.onNodeWithText("Add habit").performSemanticsAction(SemanticsActions.OnClick)

        val row = isToggleable() and hasText("Drink water")
        awaitNode(row)
        compose.onNode(row).assertIsOff()
        awaitNode(hasText("0 of 1"), unmerged = true)

        compose.onNode(row).performClick()
        awaitNode(hasText("1 of 1"), unmerged = true)
        compose.onNode(row).assertIsOn()

        compose.onNode(row).performClick()
        awaitNode(hasText("0 of 1"), unmerged = true)
        compose.onNode(row).assertIsOff()
    }
}
