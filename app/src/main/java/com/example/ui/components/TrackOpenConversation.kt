package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.push.OpenConversation

/**
 * Marks conversation [id] as on screen while the app is in the foreground. [onReload] runs when
 * the app comes back to it and when a push announces something new for it.
 */
@Composable
fun TrackOpenConversation(id: String, onReload: () -> Unit) {
    val reload = rememberUpdatedState(onReload)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, id) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    OpenConversation.opened(id)
                    // Back from the background: catch up on what arrived meanwhile.
                    if (paused) reload.value()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    paused = true
                    OpenConversation.closed(id)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            OpenConversation.closed(id)
        }
    }
    LaunchedEffect(id) {
        OpenConversation.arrived.collect { if (it == id) reload.value() }
    }
}
