package com.winlator.cmod.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val primary = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkStyle = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)

    fun link(text: String, url: String): AnnotatedString = buildAnnotatedString {
        pushStringAnnotation("URL", url)
        withStyle(linkStyle) { append(text) }
        pop()
    }

    @Composable
    fun SectionCard(title: String, content: AnnotatedString) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                )
                Spacer(Modifier.height(8.dp))
                ClickableText(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor, lineHeight = 24.sp),
                    onClick = { offset ->
                        content.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                            try { uriHandler.openUri(it.item) } catch (_: Exception) {}
                        }
                    },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logowi),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = bodyColor,
                )
            }
        }

        // Creators
        SectionCard(stringResource(R.string.credits_and_third_party_apps), buildAnnotatedString {
            append("Winlator Bionic REF4IK MOD by ref4ik\n")
            append("Telegram: "); append(link("t.me/winlatorruu", "https://t.me/winlatorruu"))
            append("\nGitHub: "); append(link("github.com/REF4IK/winlator-ref4ik-bionic", "https://github.com/REF4IK/winlator-ref4ik-bionic"))
            append("\n\n")
            append("BrunoSX – Creator Winlator ("); append(link("github.com/brunodev85", "https://github.com/brunodev85")); append(")")
            append("\nCoffincolors – Winlator cmod ("); append(link("Fork", "https://github.com/coffincolors/winlator")); append(")")
            append("\nPipetto-crypto – Winlator bionic ("); append(link("winlator_bionic", "https://github.com/Pipetto-crypto/winlator_bionic")); append(")")
            append("\nDale Melvin Blevens III – Music ("); append(link("Fumer", "https://github.com/Fumer")); append(")")
        })

        // Components
        SectionCard("Components", buildAnnotatedString {
            append(link("Ubuntu RootFs (Focal Fossa)", "https://releases.ubuntu.com/20.04/")); append("\n")
            append(link("Wine (winehq.org)", "https://www.winehq.org/")); append("\n")
            append("Box86/Box64 by "); append(link("ptitseb", "https://github.com/ptitseb")); append("\n")
            append(link("FEX-Emu", "https://github.com/FEX-Emu/FEX")); append("\n")
            append(link("PRoot", "https://proot-me.github.io/")); append("\n")
            append(link("Mesa (Turnip/Zink/VirGL)", "https://www.mesa3d.org/")); append("\n")
            append(link("DXVK", "https://github.com/doitsujin/dxvk")); append("\n")
            append(link("VKD3D", "https://gitlab.winehq.org/wine/vkd3d")); append("\n")
            append(link("D8VK", "https://github.com/AlpyneDreams/d8vk")); append("\n")
            append(link("linux-fg", "https://github.com/xXJSONDeruloXx/linux-fg")); append("\n")
            append(link("CNC DDraw", "https://github.com/FunkyFr3sh/cnc-ddraw")); append("\n")
            append(link("WinlatorWCFHub by Arihany", "https://github.com/Arihany/WinlatorWCFHub"))
        })

        // GPU Drivers
        SectionCard("GPU Drivers", buildAnnotatedString {
            append(link("K11MCH1 AdrenoTools", "https://github.com/K11MCH1/AdrenoToolsDrivers")); append("\n")
            append(link("MrPurple666 Purple Turnip", "https://github.com/MrPurple666/purple-turnip")); append("\n")
            append(link("Weab-chan Freedreno Turnip CI", "https://github.com/Weab-chan/freedreno_turnip-CI")); append("\n")
            append(link("crueter GameHub 8Elite", "https://github.com/crueter/GameHub-8Elite-Drivers")); append("\n")
            append(link("StevenMXZ Adreno Drivers", "https://github.com/StevenMXZ/Adreno-Tools-Drivers"))
        })

        // Glibc
        SectionCard(stringResource(R.string.glibc_exp_edition), buildAnnotatedString {
            append("longjunyu2's ("); append(link("Fork", "https://github.com/longjunyu2")); append(")")
        })
    }
}
