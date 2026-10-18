package app.ritualhabits.ui.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stage 3: exposes only the live habit day. The checklist arrives in Stage 4. */
class TodayViewModel(val today: StateFlow<LocalDate>) : ViewModel()

/** "Saturday, 18 October", localised day and month names. */
fun formatHabitDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date.toJavaLocalDate())

@Composable
fun TodayRoute(
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    ScreenColumn(bottomInset) {
        ScreenHeader(title = "Today", overline = formatHabitDay(today), actions = { SettingsButton(onOpenSettings) })
        Spacer(Modifier.height(Spacing.xl))
        EmptyState(
            icon = RitualIcons.Today,
            title = "Your daily checklist",
            body = "Habits and today's progress will appear here.",
        )
    }
}
