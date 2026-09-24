package com.canonreader.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.withTimeoutOrNull

private class SelectionClearCoordinator {
    var version by mutableStateOf(0)
        private set

    fun clearAll() {
        version += 1
    }
}

private val LocalSelectionClearCoordinator = compositionLocalOf { SelectionClearCoordinator() }

@Composable
fun SelectionClearProvider(
    clearKey: Any? = Unit,
    content: @Composable () -> Unit,
) {
    val coordinator = remember { SelectionClearCoordinator() }
    LaunchedEffect(clearKey) { coordinator.clearAll() }
    CompositionLocalProvider(LocalSelectionClearCoordinator provides coordinator) {
        content()
    }
}

@Composable
fun SelectableSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coordinator = LocalSelectionClearCoordinator.current
    val viewConfiguration = LocalViewConfiguration.current
    val toolbar = rememberSelectionTextToolbar(
        clearAllSelections = coordinator::clearAll,
    )
    var localResetKey by remember { mutableStateOf(0) }

    fun resetSelectionContainer() {
        localResetKey += 1
        toolbar.hide()
    }

    LaunchedEffect(coordinator.version) { resetSelectionContainer() }

    DisposableEffect(Unit) {
        onDispose { toolbar.hide() }
    }

    val clearOnScroll = remember(coordinator) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) coordinator.clearAll()
                return Offset.Zero
            }
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        key(localResetKey) {
            SelectionContainer(
                modifier = modifier
                    .nestedScroll(clearOnScroll)
                    .clearSelectionOnPlainTap(
                        longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                        clearAll = coordinator::clearAll,
                    ),
            ) {
                content()
            }
        }
    }
}

private fun Modifier.clearSelectionOnPlainTap(
    longPressTimeoutMillis: Long,
    clearAll: () -> Unit,
): Modifier = pointerInput(longPressTimeoutMillis, clearAll) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Final)
        val up = withTimeoutOrNull(longPressTimeoutMillis) {
            waitForUpOrCancellation(pass = PointerEventPass.Final)
        }
        if (up != null && !up.isConsumed) clearAll()
    }
}

@Composable
private fun rememberSelectionTextToolbar(
    clearAllSelections: () -> Unit,
): TextToolbar {
    val platformToolbar = LocalTextToolbar.current
    return remember(platformToolbar, clearAllSelections) {
        ClearingTextToolbar(
            delegate = platformToolbar,
            clearAllSelections = clearAllSelections,
        )
    }
}

private class ClearingTextToolbar(
    private val delegate: TextToolbar,
    private val clearAllSelections: () -> Unit,
) : TextToolbar {
    override val status: TextToolbarStatus
        get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested?.let { copy ->
                {
                    copy()
                    clearAllSelections()
                }
            },
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
        )
    }

    override fun hide() {
        delegate.hide()
    }
}
