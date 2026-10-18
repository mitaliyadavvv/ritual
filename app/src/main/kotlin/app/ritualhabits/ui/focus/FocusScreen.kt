package app.ritualhabits.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.TimerDial
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing

/**
 * Focus placeholder. Timed sessions are not built yet (FocusRepository and the timer rules exist,
 * the screen does not). Shows the dial the timer will use, clearly dimmed, decorative and without
 * controls, so nothing here looks actionable.
 */
@Composable
fun FocusPlaceholder(bottomInset: PaddingValues, onOpenSettings: () -> Unit) {
    val c = Ritual.colors
    ScreenColumn(bottomInset) {
        ScreenHeader(title = "Focus", actions = { SettingsButton(onOpenSettings) })
        Spacer(Modifier.height(Spacing.xxl))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TimerDial(
                progress = 0f,
                time = "25:00",
                modifier = Modifier
                    .alpha(0.35f)
                    .clearAndSetSemantics { },
            )
            Spacer(Modifier.height(Spacing.xxl))
            Text("Focus sessions", style = RitualType.heading, color = c.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Timed sessions for a habit will appear here in a later update.",
                style = RitualType.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = Spacing.lg),
            )
        }
    }
}
