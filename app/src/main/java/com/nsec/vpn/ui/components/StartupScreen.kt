package com.nsec.vpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nsec.vpn.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StartupScreen() {
    Surface(modifier = Modifier.fillMaxSize().testTag("startup_screen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.nsec_launcher_legacy), contentDescription = "Nsec VPN", modifier = Modifier.size(112.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(bottom = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Made in Europe", color = MaterialTheme.colorScheme.onBackground)
                Canvas(Modifier.size(42.dp, 28.dp).semantics { contentDescription = "European Union Flag" }) {
                    drawRect(Color(0xFF003399))
                    val ring = size.height * 0.32f
                    repeat(12) { star ->
                        val angle = star * Math.PI / 6 - Math.PI / 2
                        val x = size.width / 2 + ring * cos(angle).toFloat()
                        val y = size.height / 2 + ring * sin(angle).toFloat()
                        val path = Path()
                        repeat(10) { point ->
                            val radius = size.height * if (point % 2 == 0) 0.065f else 0.026f
                            val a = point * Math.PI / 5 - Math.PI / 2
                            val px = x + radius * cos(a).toFloat()
                            val py = y + radius * sin(a).toFloat()
                            if (point == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, Color(0xFFFFCC00))
                    }
                }
            }
        }
    }
}
