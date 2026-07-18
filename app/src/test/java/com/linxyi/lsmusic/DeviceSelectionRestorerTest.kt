package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.restoreDeviceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSelectionRestorerTest {
    @Test
    fun restoreDeviceSelection_keepsRememberedDeviceWhileOtherDevicesAreDiscovered() {
        assertEquals(
            "remembered",
            restoreDeviceSelection(
                currentId = "remembered",
                rememberedId = "remembered",
                fallbackId = "first-discovered",
                isSearching = true,
                initialSelectionResolved = false,
                userSelectedDevice = false,
            ),
        )
    }

    @Test
    fun restoreDeviceSelection_waitsForSearchBeforeSelectingFirstDeviceWithoutHistory() {
        assertNull(
            restoreDeviceSelection(
                currentId = null,
                rememberedId = null,
                fallbackId = "first-discovered",
                isSearching = true,
                initialSelectionResolved = false,
                userSelectedDevice = false,
            ),
        )
    }

    @Test
    fun restoreDeviceSelection_selectsFirstDeviceAfterInitialSearchWithoutHistory() {
        assertEquals(
            "first-discovered",
            restoreDeviceSelection(
                currentId = null,
                rememberedId = null,
                fallbackId = "first-discovered",
                isSearching = false,
                initialSelectionResolved = false,
                userSelectedDevice = false,
            ),
        )
    }

    @Test
    fun restoreDeviceSelection_doesNotOverwriteSelectionMadeDuringSearch() {
        assertEquals(
            "user-selected",
            restoreDeviceSelection(
                currentId = "user-selected",
                rememberedId = "remembered",
                fallbackId = "first-discovered",
                isSearching = true,
                initialSelectionResolved = false,
                userSelectedDevice = true,
            ),
        )
    }

    @Test
    fun restoreDeviceSelection_keepsUnavailableRememberedDeviceAfterSearch() {
        assertEquals(
            "remembered",
            restoreDeviceSelection(
                currentId = "remembered",
                rememberedId = "remembered",
                fallbackId = "another-device",
                isSearching = false,
                initialSelectionResolved = false,
                userSelectedDevice = false,
            ),
        )
    }
}
