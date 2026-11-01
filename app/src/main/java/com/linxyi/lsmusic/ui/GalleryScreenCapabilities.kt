package com.linxyi.lsmusic.ui

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

internal fun Context.hasMultipleGalleryScreens(): Boolean {
    val displayManager = getSystemService(DisplayManager::class.java)
    val builtInDisplayCount = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
    ) {
        // Includes inactive panels, so a closed foldable is not mistaken for a single screen.
        displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS).size.takeIf { it > 0 }
    } else {
        null
    }
    return shouldShowPerScreenGalleryHint(
        builtInDisplayCount = builtInDisplayCount,
        publicDisplayCount = displayManager.displays.count { it.flags and Display.FLAG_PRIVATE == 0 },
        hasHingeAngleSensor = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE),
    )
}

internal fun shouldShowPerScreenGalleryHint(
    builtInDisplayCount: Int?,
    publicDisplayCount: Int,
    hasHingeAngleSensor: Boolean,
): Boolean = publicDisplayCount > 1 ||
    // Older Android versions cannot enumerate inactive built-in panels; use the foldable heuristic.
    (builtInDisplayCount?.let { it > 1 } ?: hasHingeAngleSensor)
