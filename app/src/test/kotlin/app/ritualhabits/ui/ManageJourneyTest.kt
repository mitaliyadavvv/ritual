package app.ritualhabits.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertTrue

/**
 * Manage end-to-end through the real app shell and Room: create, reorder, archive, restore,
 * delete with confirmation. Sheets, menus and dialogs are separate windows where Robolectric
 * doesn't route touches, so their buttons are invoked through their OnClick semantics action.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ManageJourneyTest {
    @get:Rule val compose = createComposeRule()

    private fun awaitCount(matcher: SemanticsMatcher, count: Int = 1) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == count }
        } catch (e: Throwable) {
            val roots = compose.onAllNodes(isRoot())
            throw AssertionError(
                "Expected $count of ${matcher.description}\n" +
                    roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString(maxDepth = 40) },
                e,
            )
        }
    }

    private fun clickInPopup(text: String) {
        awaitCount(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        compose.onNode(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun addHabit(name: String, fromEmptyState: Boolean) {
        if (fromEmptyState) compose.onNodeWithText("Add a habit").performClick()
        else compose.onNodeWithContentDescription("Add habit").performClick()
        val field = hasSetTextAction() and hasText("Name")
        awaitCount(field)
        compose.onNode(field).performTextInput(name)
        clickInPopup("Add habit")
        awaitCount(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    private fun rowOrder(vararg names: String): List<Int> =
        names.map { compose.onAllNodesWithText(it).fetchSemanticsNodes().first().boundsInRoot.top.toInt() }

    @Test fun `create, reorder, archive, restore and delete`() {
        compose.setContent { RitualTheme { RitualAppRoot() } }
        compose.onNode(hasText("Manage") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()

        awaitCount(hasText("No habits yet"))
        addHabit("Read", fromEmptyState = true)
        addHabit("Walk", fromEmptyState = false)
        awaitCount(hasText("Active · 2"))

        // Reorder: move Walk above Read.
        compose.onNodeWithText("Reorder").performClick()
        awaitCount(hasContentDescription("Move Walk up"))
        compose.onNodeWithContentDescription("Move Walk up").performClick()
        compose.waitUntil(15_000) { rowOrder("Walk", "Read").let { it[0] < it[1] } }
        compose.onNodeWithText("Done").performClick()

        // Archive Read from its menu.
        compose.onNodeWithContentDescription("More options for Read").performClick()
        clickInPopup("Archive")
        awaitCount(hasText("Archived · 1"))
        compose.onNodeWithText("Archived · 1").performClick()
        awaitCount(hasText("Not shown on Today. History is kept."))

        // Restore it.
        compose.onNodeWithContentDescription("More options for Read").performClick()
        clickInPopup("Restore")
        awaitCount(hasText("Archived · 0"))

        // Delete Walk: cancelling keeps it, confirming removes it.
        compose.onNodeWithText("Active · 2").performClick()
        compose.onNodeWithContentDescription("More options for Walk").performClick()
        clickInPopup("Delete…")
        awaitCount(hasText("Delete “Walk”?"))
        clickInPopup("Cancel")
        awaitCount(hasText("Delete “Walk”?"), count = 0)
        awaitCount(hasText("Active · 2"))

        compose.onNodeWithContentDescription("More options for Walk").performClick()
        clickInPopup("Delete…")
        clickInPopup("Delete")
        awaitCount(hasText("Active · 1"))
        assertTrue(compose.onAllNodesWithText("Walk").fetchSemanticsNodes().isEmpty())
    }
}
