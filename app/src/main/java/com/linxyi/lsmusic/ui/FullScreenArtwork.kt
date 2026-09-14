package com.linxyi.lsmusic.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.linxyi.lsmusic.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
internal fun FullScreenArtwork(uri: String, title: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as DialogWindowProvider).window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            window.setBackgroundDrawableResource(android.R.color.black)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.systemBarsBehavior = previousBehavior
                // Only the dialog window is changed; the activity keeps its normal system bars.
            }
        }
        val progress = remember { Animatable(0f) }
        PredictiveBackHandler { events ->
            try {
                events.collect { progress.snapTo(it.progress) }
                onDismiss()
            } finally {
                withContext(NonCancellable) { progress.animateTo(0f, tween(180)) }
            }
        }
        val closeLabel = stringResource(R.string.close_full_screen_artwork)
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) }
                .semantics { onClick(label = closeLabel) { onDismiss(); true } },
        ) {
            AsyncImage(
                model = uri,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1f - progress.value * .15f
                    scaleY = scaleX
                    alpha = 1f - progress.value * .4f
                },
            )
        }
    }
}
