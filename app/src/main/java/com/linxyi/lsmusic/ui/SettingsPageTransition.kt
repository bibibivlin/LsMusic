package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

/** Keeps settings motion inside the page viewport; navigation and the mini player stay still. */
@Composable
internal fun SettingsPageTransition(destination: AppDestination, content: @Composable (AppDestination) -> Unit) {
    val pages = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = destination,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState.settingsDepth < initialState.settingsDepth) -1 else 1
            ((slideInHorizontally(tween(220)) { direction * it / 12 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(180)) { -direction * it / 12 } + fadeOut(tween(150))))
                .using(null)
        },
        label = "settings-page-transition",
    ) { page ->
        pages.SaveableStateProvider(page.name) { content(page) }
    }
}

private val AppDestination.settingsDepth: Int
    get() = when (this) {
        AppDestination.SETTINGS -> 0
        AppDestination.PENDING_LISTENS -> 2
        else -> 1
    }
