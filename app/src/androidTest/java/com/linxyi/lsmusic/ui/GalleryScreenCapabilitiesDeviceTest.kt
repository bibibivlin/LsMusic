package com.linxyi.lsmusic.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNotNull
import org.junit.Test

class GalleryScreenCapabilitiesDeviceTest {
    @Test
    fun screenDetection_matchesTheConnectedDevice() {
        // Supply the known hardware expectation when running on a single-screen or foldable device.
        val expected = InstrumentationRegistry.getArguments().getString("expectedMultipleGalleryScreens")
        assumeNotNull(expected)
        assertEquals(
            requireNotNull(expected).toBooleanStrict(),
            InstrumentationRegistry.getInstrumentation().targetContext.hasMultipleGalleryScreens(),
        )
    }
}
