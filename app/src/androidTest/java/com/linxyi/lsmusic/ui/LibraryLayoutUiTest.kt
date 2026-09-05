package com.linxyi.lsmusic.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class LibraryLayoutUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun layoutToggleRightHalfWorksWhileFastScrollerIsVisible() {
        setLibraryContent()

        compose.onNodeWithContentDescription(text(R.string.fast_scroll_library)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.switch_to_grid))
            .assertIsDisplayed()
            .performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }

        compose.onNodeWithContentDescription(text(R.string.switch_to_list))
            .assertIsDisplayed()
            .performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }

        compose.onNodeWithContentDescription(text(R.string.switch_to_grid)).assertIsDisplayed()
    }

    @Test
    fun fastScrollerStillDragsAndKeepsFloatingSearchClickable() {
        setLibraryContent()

        compose.onNodeWithContentDescription(text(R.string.fast_scroll_library))
            .assertIsDisplayed()
            .performTouchInput {
                swipe(
                    start = center,
                    end = Offset(centerX, bottom),
                    durationMillis = 300L,
                )
            }

        compose.onNodeWithContentDescription(text(R.string.search_current_directory))
            .assertIsDisplayed()
            .performClick()

        compose.onNodeWithContentDescription(text(R.string.switch_to_grid)).assertIsDisplayed()
    }

    private fun setLibraryContent() {
        val pageKey = BrowsePageKey("server", "albums")
        val viewState = BrowseViewState(useGrid = false)
        val server = DlnaDevice(
            id = "server",
            name = "Test library",
            manufacturer = "Test",
            model = "Server",
            kind = DlnaDeviceKind.MEDIA_SERVER,
        )
        val state = LibraryUiState(
            entries = List(60) { index ->
                MediaEntry(
                    id = "album-$index",
                    parentId = "albums",
                    title = "Album $index",
                    creator = "Artist $index",
                    isContainer = true,
                    isAlbum = true,
                )
            },
            albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("albums", "Albums")),
            browsePageKey = pageKey,
            browseViewState = viewState,
            preferences = AppPreferences(useGridByDefault = false),
            isSearching = false,
            servers = listOf(server),
            selectedServerId = server.id,
            browseLoadStatus = BrowseLoadStatus.LOADED,
            albumArtwork = null,
            currentTrackId = null,
            playbackState = RemotePlaybackState.STOPPED,
        )

        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                LibraryDirectoryScreen(
                    state = state,
                    modifier = Modifier.requiredSize(width = 412.dp, height = 700.dp),
                    pageKey = pageKey,
                    initialViewState = viewState,
                    onOpen = {},
                    onNavigateTo = {},
                    onPlay = {},
                    onQueue = {},
                    onPlayAll = {},
                    onShufflePlay = {},
                    onQueueAll = {},
                    onAlbumSort = {},
                    onSaveBrowseViewState = { _, _ -> },
                    bottomContentPadding = 32.dp,
                    onOpenSettings = {},
                )
            }
        }
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
