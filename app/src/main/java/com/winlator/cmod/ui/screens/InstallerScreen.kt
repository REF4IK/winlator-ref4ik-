package com.winlator.cmod.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R

@Composable
fun InstallerScreen(
    progress: Int,
    isFinished: Boolean
) {
    // Smooth progress bar animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.logowi),
                contentDescription = "Winlator Logo",
                modifier = Modifier
                    .width(220.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Unpacking Text Status
            Text(
                text = if (isFinished) stringResource(id = R.string.inst_setup_finished) else stringResource(id = R.string.installing_system_files),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.inst_please_wait),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Premium Linear Progress Indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Percentage Text
            Text(
                text = "$progress%",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
