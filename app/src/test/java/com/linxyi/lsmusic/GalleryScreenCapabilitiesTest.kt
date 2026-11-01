package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.shouldShowPerScreenGalleryHint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryScreenCapabilitiesTest {
    @Test
    fun singleScreen_hidesHintEvenIfThePanelCanFold() {
        assertFalse(shouldShowPerScreenGalleryHint(1, 1, false))
        assertFalse(shouldShowPerScreenGalleryHint(1, 1, true))
    }

    @Test
    fun foldable_showsHintEvenWithOnlyOneActivePanel() {
        assertTrue(shouldShowPerScreenGalleryHint(2, 1, false))
    }

    @Test
    fun olderAndroid_usesHingeCapabilityWhenInactivePanelsCannotBeEnumerated() {
        assertTrue(shouldShowPerScreenGalleryHint(null, 1, true))
        assertFalse(shouldShowPerScreenGalleryHint(null, 1, false))
    }

    @Test
    fun externalScreen_showsHintOnlyWhileConnected() {
        assertTrue(shouldShowPerScreenGalleryHint(1, 2, false))
        assertTrue(shouldShowPerScreenGalleryHint(null, 2, false))
        assertFalse(shouldShowPerScreenGalleryHint(1, 1, false))
    }
}
