package app.ritualhabits.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.data.repository.FirstRunStage
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.settings.AccountViewModel
import app.ritualhabits.ui.theme.Ritual

/** First launch: onboarding, then sign-in. [stage] comes from the saved first-run state. */
@Composable
fun WelcomeFlow(
    stage: FirstRunStage,
    welcome: WelcomeViewModel = viewModel(factory = RitualViewModels.Factory),
    accountViewModel: AccountViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()
    val restoring by welcome.restoring.collectAsStateWithLifecycle()
    // Coming back from sign-in lands on the last onboarding page, not the first.
    var returnedFromSignIn by rememberSaveable { mutableStateOf(false) }
    val motion = Ritual.motion

    AnimatedContent(
        targetState = stage == FirstRunStage.SIGN_IN,
        transitionSpec = {
            if (motion.reduced) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                val forward = targetState
                (slideInHorizontally(motion.emphasized<IntOffset>()) { if (forward) it / 6 else -it / 6 } + fadeIn(motion.emphasized()))
                    .togetherWith(fadeOut(motion.exit()) + slideOutHorizontally(motion.exit<IntOffset>()) { if (forward) -it / 8 else it / 8 })
            }
        },
        label = "welcome",
    ) { signIn ->
        if (signIn) {
            SignInScreen(
                account = account,
                restoring = restoring,
                onSignIn = accountViewModel::signIn,
                onChooseAccount = accountViewModel::chooseAccount,
                onNotNow = { welcome.continueWithoutAccount() },
                onBack = {
                    returnedFromSignIn = true
                    welcome.backToOnboarding()
                },
            )
        } else {
            OnboardingScreen(
                onFinish = { welcome.leaveOnboarding() },
                initialPage = if (returnedFromSignIn) OnboardingPage.entries.lastIndex else 0,
            )
        }
    }
}
