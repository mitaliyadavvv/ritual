package app.ritualhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualMotion
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Spinner shown only if loading takes longer than a moment, so fast loads don't flicker. */
@Composable
fun DelayedLoadingIndicator(label: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    Box(modifier.fillMaxWidth().padding(top = Spacing.xxxl), contentAlignment = Alignment.Center) {
        if (visible) {
            CircularProgressIndicator(
                color = Ritual.colors.textSecondary,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = label },
            )
        }
    }
}

/** Inverted snackbar (near-black on light, near-white on dark), floating above the tab bar. */
@Composable
fun RitualSnackbarHost(state: SnackbarHostState, bottomInset: PaddingValues, modifier: Modifier = Modifier) {
    SnackbarHost(
        state,
        modifier
            .padding(bottomInset)
            .padding(horizontal = Ritual.layout.gutter),
    ) { data ->
        Snackbar(
            data,
            shape = RoundedCornerShape(Radii.md),
            containerColor = Ritual.colors.accent,
            contentColor = Ritual.colors.onAccent,
            actionColor = Ritual.colors.onAccent,
        )
    }
}

/** Monochrome confirmation dialog. The confirm action is the filled button. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ritual.colors.surface,
        titleContentColor = Ritual.colors.textPrimary,
        textContentColor = Ritual.colors.textSecondary,
        shape = RoundedCornerShape(Radii.xl),
        tonalElevation = 0.dp,
        title = { Text(title, style = RitualType.heading) },
        text = { Text(body, style = RitualType.bodySmall) },
        confirmButton = { PrimaryButton(confirmLabel, onClick = onConfirm) },
        dismissButton = { SecondaryButton(dismissLabel, onClick = onDismiss) },
    )
}

data class MenuAction(val label: String, val onClick: () -> Unit)

/** Opaque floating menu: soft shadow in light, hairline edge in dark; items are ≥ 48 dp tall. */
@Composable
fun RitualMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    val c = Ritual.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radii.md),
        containerColor = c.surface,
        tonalElevation = 0.dp,
        shadowElevation = if (c.isDark) 0.dp else 12.dp,
        border = if (c.isDark) BorderStroke(Sizes.hairline, c.border) else null,
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label, style = RitualType.rowTitle) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
                colors = MenuDefaults.itemColors(textColor = c.textPrimary),
                contentPadding = PaddingValues(horizontal = Spacing.lg),
            )
        }
    }
}

/**
 * Animates an item from its previous position to its new one when siblings are reordered
 * (non-lazy layouts). Snaps when system animations are off.
 */
fun Modifier.animatePlacement(motion: RitualMotion): Modifier = composed {
    if (motion.reduced) return@composed this
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val t = target ?: return@offset IntOffset.Zero
            val anim = animatable ?: Animatable(t, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != t) scope.launch { anim.animateTo(t, motion.standard()) }
            anim.value - t
        }
}
