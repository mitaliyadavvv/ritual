package app.ritualhabits.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.BuildConfig
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.ListRow
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualSwitch
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.SectionLabel
import app.ritualhabits.ui.components.SegmentedChoice
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing

@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = RitualViewModels.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack, viewModel::setThemeMode, viewModel::setHapticsEnabled)
}

/**
 * Stage 3 scope: appearance and haptics (needed to verify the design system).
 * Profile name and "Delete all local data" are added with the Settings feature in Stage 4.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onHaptics: (Boolean) -> Unit,
) {
    val haptics = Ritual.haptics
    ScreenColumn(bottomInset = WindowInsets.navigationBars.asPaddingValues()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RitualIconButton(RitualIcons.Back, contentDescription = "Back", onClick = onBack, modifier = Modifier.offset(x = -Spacing.xs))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Settings",
            style = RitualType.title,
            color = Ritual.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xl))

        SectionLabel("Appearance")
        SegmentedChoice(
            options = ThemeMode.entries,
            selected = state.themeMode,
            label = { it.label },
            onSelect = onThemeMode,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "System follows your device's light or dark setting.",
            style = RitualType.bodySmall,
            color = Ritual.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.lg),
        )

        Spacer(Modifier.height(Spacing.xxl))
        SectionLabel("Feedback")
        Module(contentPadding = PaddingValues(horizontal = Spacing.lg)) {
            ListRow(
                title = "Haptic feedback",
                supporting = "Subtle vibrations when you complete habits and change controls.",
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.hapticsEnabled, role = Role.Switch) { enabled ->
                        // Confirm the change before haptics are turned off.
                        haptics.perform(if (enabled) HapticEvent.ToggleOn else HapticEvent.ToggleOff)
                        onHaptics(enabled)
                    },
                trailing = { RitualSwitch(checked = state.hapticsEnabled, onCheckedChange = null) },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        SectionLabel("About")
        Module(contentPadding = PaddingValues(horizontal = Spacing.lg)) {
            ListRow(title = "Version", trailing = { Text(BuildConfig.VERSION_NAME, style = RitualType.bodySmall, color = Ritual.colors.textSecondary) })
            Hairline()
            ListRow(
                title = "Your data",
                supporting = "Ritual works offline. Habits and history are stored only on this device.",
            )
        }
    }
}

val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
