package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyLayoutScrollScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LibrarySearchControlHeight = 56.dp
internal val LibraryFloatingSearchTop = 20.dp
internal val LibraryFloatingSearchResultInset = LibraryFloatingSearchTop + LibrarySearchControlHeight + 12.dp
private val LibrarySearchMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun librarySearchReturnDuration(distanceDp: Float): Int =
    (260f + distanceDp.coerceIn(0f, 800f) * 0.25f).roundToInt()

/** Re-estimate the remaining distance until the header is visible, without teleporting items. */
internal suspend fun LazyGridState.animateSearchReturnToTop(density: Density): Boolean {
    var reachedTop = false
    scroll {
        val layoutScroll = LazyLayoutScrollScope(this@animateSearchReturnToTop, this)
        fun remainingDistance(): Float = if (firstVisibleItemIndex == 0) {
            -firstVisibleItemScrollOffset.toFloat()
        } else {
            layoutScroll.calculateDistanceTo(0).toFloat().coerceAtMost(0f)
        }

        var passes = 0
        while ((firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) && passes < 4) {
            val initialDistance = remainingDistance()
            if (initialDistance == 0f) break
            var previousProgress = 0f
            var consumedDistance = 0f
            val duration = librarySearchReturnDuration(abs(initialDistance) / density.density)
            animate(0f, 1f, animationSpec = tween(duration, easing = LibrarySearchMotionEasing)) { progress, _ ->
                val remainingProgress = (1f - previousProgress).coerceAtLeast(0.0001f)
                val portion = ((progress - previousProgress) / remainingProgress).coerceIn(0f, 1f)
                consumedDistance += abs(layoutScroll.scrollBy(remainingDistance() * portion))
                previousProgress = progress
            }
            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= 1) {
                layoutScroll.snapToItem(0)
            }
            if (consumedDistance < 0.5f) break
            passes++
        }
        reachedTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
    }
    return reachedTop
}

/** A single surface expands from the search button and contracts back to it. */
@Composable
internal fun LibraryFloatingSearch(
    expanded: Boolean,
    showButton: Boolean,
    expandedWidth: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onImeSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(expanded, label = "library-search-morph")
    val width by transition.animateDp(
        transitionSpec = {
            tween(if (targetState) 400 else 320, easing = LibrarySearchMotionEasing)
        },
        label = "library-search-width",
    ) { if (it) expandedWidth else LibrarySearchControlHeight }
    val fieldAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(180, delayMillis = 60, easing = LibrarySearchMotionEasing)
            else tween(140, easing = LibrarySearchMotionEasing)
        },
        label = "library-search-field-alpha",
    ) { if (it) 1f else 0f }
    val buttonAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(160, easing = LibrarySearchMotionEasing)
            else tween(150, delayMillis = 80, easing = LibrarySearchMotionEasing)
        },
        label = "library-search-button-alpha",
    ) { if (it) 0f else 1f }
    val buttonColor = MaterialTheme.colorScheme.secondaryContainer
    val fieldColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor by transition.animateColor(
        transitionSpec = {
            tween(if (targetState) 400 else 320, easing = LibrarySearchMotionEasing)
        },
        label = "library-search-color",
    ) { if (it) fieldColor else buttonColor }
    val focusRequester = remember { FocusRequester() }
    val searchHint = stringResource(R.string.search_current_directory)

    LaunchedEffect(expanded) {
        if (expanded) {
            // The field is present before focus is requested, including after an interrupted morph.
            delay(180)
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = showButton || expanded || transition.currentState || transition.isRunning,
        modifier = modifier,
        enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = LibrarySearchMotionEasing)),
        exit = fadeOut(tween(160)) + scaleOut(tween(180, easing = LibrarySearchMotionEasing)),
    ) {
        Surface(
            modifier = Modifier
                .width(width)
                .height(LibrarySearchControlHeight)
                .testTag("library-floating-search"),
            shape = RoundedCornerShape(LibrarySearchControlHeight / 2),
            color = containerColor,
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (expanded || transition.currentState || transition.isRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fieldAlpha }
                            .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            enabled = expanded,
                            modifier = Modifier.size(LibrarySearchControlHeight).testTag("library-close-floating-search"),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.close_search))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            enabled = expanded,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = searchHint }
                                .testTag("library-floating-search-input"),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = searchHint,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, enabled = expanded) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                            }
                        } else {
                            Box(Modifier.width(16.dp))
                        }
                    }
                }
                if (!expanded || !transition.currentState) {
                    IconButton(
                        onClick = onOpen,
                        enabled = !expanded,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(LibrarySearchControlHeight)
                            .graphicsLayer { alpha = buttonAlpha }
                            .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier)
                            .testTag("library-floating-search-button"),
                    ) {
                        Icon(Icons.Rounded.Search, searchHint, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
