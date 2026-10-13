package com.nsec.vpn.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Observe CREATED too, so moving to the background stops visual work immediately. */
@Composable
fun isUiResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    return state.isAtLeast(Lifecycle.State.RESUMED)
}
