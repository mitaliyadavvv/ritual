package app.ritualhabits.ui.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.ProgressRing
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.settings.AccountSwitchDialog
import app.ritualhabits.ui.settings.AccountUiState
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing

/**
 * The welcome's last step: "Continue with Google" through the app's existing sign-in and backup
 * (the same AccountViewModel as Settings). "Not now" keeps the local-only experience; sign-in stays
 * available in Settings.
 */
@Composable
fun SignInScreen(
    account: AccountUiState,
    restoring: Boolean,
    onSignIn: (Context) -> Unit,
    onChooseAccount: (Boolean) -> Unit,
    onNotNow: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val context = LocalContext.current
    val busy = account.signingIn || account.account == AccountState.Loading || account.account is AccountState.ChooseAccount
    BackHandler(enabled = !restoring, onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = Ritual.layout.contentMaxWidth).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Ritual.layout.gutter - Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!restoring) RitualIconButton(RitualIcons.Back, contentDescription = "Back", onClick = onBack)
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Ritual.layout.gutter),
                verticalArrangement = Arrangement.Center,
            ) {
                // A finished day's ring: the app's own "all done" mark.
                ProgressRing(progress = 1f, size = 88.dp, strokeWidth = 8.dp, modifier = Modifier.clearAndSetSemantics { }) {
                    Icon(RitualIcons.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(Spacing.xxl))
                Text(
                    "Your rituals, always with you.",
                    style = RitualType.title,
                    color = c.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "Sign in to back up your habits and progress, and pick up where you left off.",
                    style = RitualType.body,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.xl))
            }

            AnimatedContent(
                targetState = restoring,
                transitionSpec = {
                    if (motion.reduced) ContentTransform(EnterTransition.None, ExitTransition.None)
                    else fadeIn(motion.standard()).togetherWith(fadeOut(motion.exit()))
                },
                label = "signInActions",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Ritual.layout.gutter)
                    .padding(bottom = Spacing.lg),
            ) { isRestoring ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isRestoring) {
                        Row(
                            Modifier
                                .heightIn(min = 52.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = c.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.md))
                            Text("Restoring your habits…", style = RitualType.body, color = c.textSecondary)
                        }
                        Spacer(Modifier.height(Spacing.xxl + Spacing.xl))
                    } else {
                        if (account.message != null) {
                            Text(
                                account.message,
                                style = RitualType.bodySmall,
                                color = c.textPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Spacing.md)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        PrimaryButton(
                            text = when {
                                busy -> "Connecting…"
                                account.message != null -> "Try again"
                                else -> "Continue with Google"
                            },
                            onClick = { onSignIn(context) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "Your habits, your progress, across reinstalls.",
                            style = RitualType.bodySmall,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        TextButton(onClick = onNotNow, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Not now", style = RitualType.label, color = c.textSecondary)
                        }
                    }
                }
            }
        }
    }

    if (account.account is AccountState.ChooseAccount) AccountSwitchDialog(account.account, onChooseAccount)
}
