package com.zapret2.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zapret2.app.BuildConfig
import com.zapret2.app.ui.components.FluentCard
import com.zapret2.app.ui.theme.*

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluentCard(modifier = Modifier.clickable { openUrl("https://github.com/TerminalExplore") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = AccentLight)
                Spacer(Modifier.width(12.dp))
                Column { 
                    Text("Fork by TerminalExplore", color = TextPrimary) 
                    Text("Zapret2 Plus - Magisk Module", fontSize = 12.sp, color = TextSecondary) 
                }
            }
        }

        FluentCard {
            Text("Zapret2 Plus", fontSize = 20.sp, color = TextPrimary)
            Text("v${BuildConfig.VERSION_NAME}", fontSize = 14.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("DPI bypass with auto-switch WiFi/Mobile, VPN support, and app filtering", fontSize = 13.sp, color = TextTertiary)
        }

        FluentCard(modifier = Modifier.clickable { openUrl("https://github.com/bol-van/zapret2") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = GithubWhite)
                Spacer(Modifier.width(12.dp))
                Column { 
                    Text("bol-van/zapret2", color = TextPrimary) 
                    Text("Original project", fontSize = 12.sp, color = TextSecondary) 
                }
            }
        }

        FluentCard(modifier = Modifier.clickable { openUrl("https://github.com/youtubediscord/magisk-zapret2") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = AccentLightBlue)
                Spacer(Modifier.width(12.dp))
                Column { 
                    Text("youtubediscord/magisk-zapret2", color = TextPrimary) 
                    Text("Android module", fontSize = 12.sp, color = TextSecondary) 
                }
            }
        }
    }
}
