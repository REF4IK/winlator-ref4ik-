package com.winlator.cmod.ui.screens

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager

/**
 * Хост для отображения существующих Java-фрагментов внутри Compose.
 * Позволяет постепенно мигрировать фрагменты на Compose
 * без потери функциональности.
 *
 * @param fragmentClass Класс фрагмента для отображения
 * @param tag Тег фрагмента (опционально)
 * @param arguments Аргументы для фрагмента (опционально)
 */
@Composable
fun FragmentHostScreen(
    fragmentClass: Class<out Fragment>,
    tag: String? = null,
    arguments: Bundle? = null,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as AppCompatActivity
    val fragmentTag = tag ?: fragmentClass.simpleName
    val containerId = remember { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { containerView ->
            val fragmentManager = activity.supportFragmentManager
            var fragment = fragmentManager.findFragmentByTag(fragmentTag)

            if (fragment == null || !fragment.isAdded) {
                fragment = fragmentClass.newInstance()
                fragment.arguments = arguments
                fragmentManager.beginTransaction()
                    .replace(containerId, fragment, fragmentTag)
                    .commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            // Не удаляем фрагмент при смене Compose состояний,
            // чтобы избежать flickering. Фрагмент переживёт рекомпозицию.
        }
    }
}

/**
 * Хост для фрагментов с поддержкой back stack.
 */
@Composable
fun FragmentHostScreenWithBackStack(
    fragmentClass: Class<out Fragment>,
    tag: String? = null,
    arguments: Bundle? = null,
    onBackPressed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as AppCompatActivity
    val fragmentTag = tag ?: fragmentClass.simpleName
    val containerId = remember { View.generateViewId() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { containerView ->
            val fragmentManager = activity.supportFragmentManager
            var fragment = fragmentManager.findFragmentByTag(fragmentTag)

            if (fragment == null || !fragment.isAdded) {
                fragment = fragmentClass.newInstance()
                fragment.arguments = arguments
                fragmentManager.beginTransaction()
                    .addToBackStack(fragmentTag)
                    .replace(containerId, fragment, fragmentTag)
                    .commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            }
        },
    )
}
