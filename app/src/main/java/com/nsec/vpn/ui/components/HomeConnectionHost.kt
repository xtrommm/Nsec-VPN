package com.nsec.vpn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Keep Home composed and measured across connection details without drawing hidden content. */
@Composable
fun HomeConnectionHost(
    showConnection: Boolean,
    home: @Composable (visible: Boolean) -> Unit,
    connection: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(Modifier.fillMaxSize().then(if (showConnection) Modifier.clearAndSetSemantics {} else Modifier)) {
                home(!showConnection)
            }
            if (showConnection) Box(Modifier.fillMaxSize()) { connection() }
        }
    ) { measurables, constraints ->
        val homeLayout = measurables[0].measure(constraints)
        val connectionLayout = if (showConnection) measurables[1].measure(constraints) else null
        layout(homeLayout.width, homeLayout.height) {
            // Unplaced Home has no drawing, pointer handling or visible ad impressions.
            if (connectionLayout != null) connectionLayout.placeRelative(0, 0)
            else homeLayout.placeRelative(0, 0)
        }
    }
}
