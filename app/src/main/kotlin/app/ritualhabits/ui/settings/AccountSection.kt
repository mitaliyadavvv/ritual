package app.ritualhabits.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.ui.components.ConfirmDialog
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.ListRow
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.SectionLabel
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing

/** "Account" block at the top of Settings: optional Google sign-in for backup and sync. */
@Composable
fun AccountSection(
    state: AccountUiState,
    clock: AppClock,
    onSignIn: (Context) -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onChooseAccount: (Boolean) -> Unit,
    activityContext: Context,
) {
    val account = state.account
    if (account == AccountState.Unavailable) return
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    SectionLabel("Account")
    Module(contentPadding = PaddingValues(horizontal = Spacing.lg)) {
        when (account) {
            AccountState.Unavailable -> Unit
            AccountState.Loading -> ListRow(title = "Backup", supporting = "Checking your account…")
            is AccountState.SignedOut, is AccountState.ChooseAccount -> {
                val previous = (account as? AccountState.SignedOut)?.previousEmail ?: (account as? AccountState.ChooseAccount)?.previousEmail
                ListRow(
                    title = if (previous == null) "Back up your habits" else "Signed out",
                    supporting = if (previous == null) {
                        "Sign in to keep your habits, history and reminders if you reinstall Ritual or change phones. Ritual works offline either way."
                    } else {
                        "The habits on this phone belong to $previous. Sign in again to resume backup; changes made meanwhile will back up then."
                    },
                )
                PrimaryButton(
                    text = if (state.signingIn || account is AccountState.ChooseAccount) "Connecting…" else "Continue with Google",
                    onClick = { onSignIn(activityContext) },
                    enabled = !state.signingIn && account !is AccountState.ChooseAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg),
                )
            }
            is AccountState.SignedIn -> {
                val user = account.user
                ListRow(
                    title = user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: "Google account",
                    supporting = listOfNotNull("Signed in with Google", user.email.takeIf { user.displayName?.isNotBlank() == true })
                        .joinToString(" · "),
                )
                Hairline()
                ListRow(
                    title = "Backup",
                    supporting = backupStatusText(account.sync, clock.now(), clock.zone()),
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = "Back up now", onClick = onSyncNow)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    trailing = {
                        if (account.sync.syncing) {
                            CircularProgressIndicator(color = Ritual.colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Back up now", style = RitualType.bodySmall, color = Ritual.colors.textSecondary)
                        }
                    },
                )
                Hairline()
                ListRow(
                    title = "Sign out",
                    modifier = Modifier.clickable(role = Role.Button) { confirmSignOut = true },
                )
            }
        }
    }
    AnimatedVisibility(state.message != null) {
        Column {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                state.message.orEmpty(),
                style = RitualType.bodySmall,
                color = Ritual.colors.textSecondary,
                modifier = Modifier
                    .padding(start = Spacing.lg)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    Spacer(Modifier.height(Spacing.xxl))

    if (account is AccountState.ChooseAccount) AccountSwitchDialog(account, onChooseAccount)
    if (confirmSignOut && account is AccountState.SignedIn) {
        val pending = account.sync.pending
        val note = when (pending) {
            0 -> ""
            1 -> " 1 change hasn't backed up yet; it will when you sign back in."
            else -> " $pending changes haven't backed up yet; they will when you sign back in."
        }
        ConfirmDialog(
            title = "Sign out?",
            body = "Your habits stay on this phone and keep working offline. Backup pauses until you sign in again.$note",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** "Switch to …?" confirmation when the phone holds another account's data (Settings and sign-in screen). */
@Composable
fun AccountSwitchDialog(account: AccountState.ChooseAccount, onChooseAccount: (Boolean) -> Unit) {
    val previous = account.previousEmail ?: "another account"
    val next = account.user.email ?: "this account"
    val unsynced = when (account.unsyncedChanges) {
        0 -> ""
        1 -> " except 1 recent change that hasn't backed up, which will be lost"
        else -> " except ${account.unsyncedChanges} recent changes that haven't backed up, which will be lost"
    }
    ConfirmDialog(
        title = "Switch to $next?",
        body = "This phone has habits from $previous. They're safe in that account$unsynced. " +
            "Switching removes them from this phone and restores $next's habits instead.",
        confirmLabel = "Switch",
        onConfirm = { onChooseAccount(true) },
        onDismiss = { onChooseAccount(false) },
    )
}

/** The "Your data" line under About. */
fun dataStorageNote(account: AccountState): String = when (account) {
    is AccountState.SignedIn ->
        "Ritual works offline. Habits and history are stored on this device and backed up to ${account.user.email ?: "your Google account"}."
    else -> "Ritual works offline. Habits and history are stored only on this device."
}
