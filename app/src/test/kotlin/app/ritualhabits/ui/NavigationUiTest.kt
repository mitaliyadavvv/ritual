package app.ritualhabits.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ritualhabits.ui.navigation.RitualAppRoot
import app.ritualhabits.ui.theme.RitualTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric Compose test of the app shell: tabs, Settings entry point, back, semantics. */
@RunWith(RobolectricTestRunner::class)
class NavigationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun tab(label: String) =
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText(label), useUnmergedTree = false)

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))

    @Test fun `tabs switch screens and settings opens on top and returns`() {
        compose.setContent { RitualTheme { RitualAppRoot() } }

        heading("Today").assertIsDisplayed()
        tab("Today").assertIsSelected()

        for (label in listOf("Progress", "Focus", "Manage", "Today")) {
            tab(label).performClick()
            compose.waitForIdle()
            heading(label).assertIsDisplayed()
            tab(label).assertIsSelected()
        }

        // Settings is an icon entry point in the header, not a fifth tab.
        compose.onNodeWithContentDescription("Settings").assertHasClickAction().performClick()
        compose.waitForIdle()
        heading("Settings").assertIsDisplayed()
        compose.onNodeWithText("Manage").assertDoesNotExist() // bottom bar hidden

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        heading("Today").assertIsDisplayed()
        tab("Today").assertIsSelected()
    }
}
