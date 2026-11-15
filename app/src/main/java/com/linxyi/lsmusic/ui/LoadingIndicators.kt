package com.linxyi.lsmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(modifier = modifier.size(size).testTag("loading-indicator"), color = color)
}

@Composable
internal fun LoadingPanel(
    message: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showLabel: Boolean = true,
) {
    val label: @Composable () -> Unit = {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    val panelModifier = modifier.widthIn(max = 360.dp).fillMaxWidth().testTag("loading-panel")
    if (!showLabel) {
        Box(panelModifier, contentAlignment = Alignment.Center) {
            AppLoadingIndicator(
                modifier = Modifier.semantics { contentDescription = message },
                size = if (compact) 32.dp else 48.dp,
            )
        }
    } else if (compact) {
        Row(panelModifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppLoadingIndicator(size = 32.dp)
            Spacer(Modifier.width(8.dp))
            label()
        }
    } else {
        Column(panelModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            AppLoadingIndicator()
            Spacer(Modifier.height(8.dp))
            label()
        }
    }
}
