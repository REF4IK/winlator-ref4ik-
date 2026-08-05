package com.winlator.cmod.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * Анимация перехода между экранами по настройке "transition_animation"
 * (none / fade / slide_horizontal / slide_vertical / zoom / scale / slide_fade)
 * и длительности "animation_duration_ms".
 */
fun screenTransition(style: String, durationMs: Int): ContentTransform {
    val duration = durationMs.coerceIn(100, 600)
    val fade = tween<Float>(duration)
    return when (style) {
        "fade" -> fadeIn(fade) togetherWith fadeOut(fade)
        "slide_horizontal" ->
            slideInHorizontally(tween(duration)) { it } togetherWith slideOutHorizontally(tween(duration)) { -it }
        "slide_vertical" ->
            slideInVertically(tween(duration)) { it } togetherWith slideOutVertically(tween(duration)) { -it }
        "zoom" ->
            (scaleIn(tween(duration), initialScale = 0.85f) + fadeIn(fade)) togetherWith
                (scaleOut(tween(duration), targetScale = 0.9f) + fadeOut(fade))
        "scale" ->
            (scaleIn(tween(duration), initialScale = 0.7f) + fadeIn(fade)) togetherWith
                (scaleOut(tween(duration), targetScale = 0.95f) + fadeOut(fade))
        "slide_fade" ->
            (slideInHorizontally(tween(duration)) { it / 3 } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(fade))
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}
