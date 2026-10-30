package com.linxyi.lsmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class AlbumDetailLayoutUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wideLayoutCentersSummaryAndAllowsLastTrackAboveFloatingActions() {
        val actions = mutableListOf<String>()
        val state = albumState()
        setAlbumContent(width = 900.dp, state = state, actions = actions)

        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithText("Album artist").assertIsDisplayed()
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        val summary = bounds("album-detail-summary")
        val actionsBounds = bounds("album-detail-floating-actions")
        val list = bounds("album-detail-tracks")
        val track = compose.onNodeWithText("Track 1").fetchSemanticsNode().boundsInRoot
        val trackQueue = compose.onAllNodesWithContentDescription(text(R.string.add_to_queue))[0]
            .fetchSemanticsNode().boundsInRoot
        val expectedSummaryCenter = (list.top + list.height * (24f / 650f) +
            list.bottom - list.height * (112f / 650f)) / 2f
        assertTrue("Summary should be left of the tracks", summary.right <= track.left)
        assertTrue(
            "Summary should be vertically centered",
            abs(summary.center.y - expectedSummaryCenter) < list.height * .02f,
        )
        assertTrue("The track list should extend behind the floating buttons", trackQueue.right > actionsBounds.left)
        assertTrue(
            "Floating buttons should clear the mini player",
            actionsBounds.bottom <= list.bottom - list.height * (80f / 650f),
        )
        assertTrue("Play all should be larger", bounds("album-action-play-all").width > bounds("album-action-shuffle").width)

        compose.onNodeWithTag("album-action-play-all")
            .assertContentDescriptionEquals(text(R.string.play_all)).performClick()
        compose.onNodeWithTag("album-action-shuffle")
            .assertContentDescriptionEquals(text(R.string.shuffle_play)).performClick()
        compose.onNodeWithTag("album-action-queue-all")
            .assertContentDescriptionEquals(text(R.string.add_to_queue)).performClick()
        compose.onNodeWithText("Track 1").performClick()
        compose.onAllNodesWithContentDescription(text(R.string.add_to_queue))[0].performClick()
        compose.runOnIdle {
            assertEquals(
                listOf("play-all:30", "shuffle:30", "queue-all:30", "play:track-1", "queue:track-1"),
                actions,
            )
        }

        compose.onNodeWithTag("album-detail-tracks").performScrollToIndex(30)
        compose.onNodeWithTag("album-detail-tracks").performTouchInput { swipeUp() }
        compose.onNodeWithTag("album-action-play-all").assertIsDisplayed()
        val lastTrack = compose.onNodeWithText("Track 30").fetchSemanticsNode().boundsInRoot
        val queueButtons = compose.onAllNodesWithContentDescription(text(R.string.add_to_queue))
        val lastQueueIndex = queueButtons.fetchSemanticsNodes().indices.minBy { index ->
            abs(queueButtons[index].fetchSemanticsNode().boundsInRoot.center.y - lastTrack.center.y)
        }
        val lastQueue = queueButtons[lastQueueIndex].fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The last track should scroll above the floating buttons: $lastTrack, $actionsBounds",
            lastTrack.bottom <= actionsBounds.top,
        )
        assertTrue(
            "The last queue button should scroll above the floating buttons: $lastQueue, $actionsBounds",
            lastQueue.bottom <= actionsBounds.top,
        )
        queueButtons[lastQueueIndex].performClick()
        compose.runOnIdle { assertEquals("queue:track-30", actions.last()) }
    }

    @Test
    fun mediumLayoutKeepsSummaryAboveActions() {
        setAlbumContent(width = 800.dp, height = 650.dp, state = albumState())

        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.play_all)).assertIsDisplayed()
        val summary = bounds("album-detail-summary")
        val actionRow = bounds("album-detail-actions")
        assertTrue("Summary should remain above the actions", summary.bottom <= actionRow.top)
    }

    @Test
    fun wideNearlySquareLayoutKeepsSummaryAboveActions() {
        setAlbumContent(width = 900.dp, height = 780.dp, state = albumState())

        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.play_all)).assertIsDisplayed()
        val summary = bounds("album-detail-summary")
        val actionRow = bounds("album-detail-actions")
        assertTrue("Summary should remain above the actions", summary.bottom <= actionRow.top)
    }

    @Test
    fun trackPositionSurvivesWidthChangeAndLeavingAlbum() {
        var width by mutableStateOf(900.dp)
        var showAlbum by mutableStateOf(true)
        var savedViewState = BrowseViewState()
        val state = albumState()
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport {
                    if (showAlbum) {
                        AlbumDetailScreen(
                            state = state,
                            modifier = Modifier.requiredSize(width, 650.dp),
                            pageKey = pageKey,
                            initialViewState = savedViewState,
                            onSaveBrowseViewState = { _, viewState -> savedViewState = viewState },
                            onResolveAlbumArtwork = { _, _ -> },
                            onPlay = {},
                            onQueue = {},
                            onPlayAll = {},
                            onShufflePlay = {},
                            onQueueAll = {},
                            bottomContentPadding = 112.dp,
                        )
                    }
                }
            }
        }

        compose.waitUntil(10_000) { compose.onNodeWithText("Test album").isDisplayed() }
        compose.onNodeWithTag("album-detail-tracks").performScrollToNode(hasText("Track 24"))
        compose.onNodeWithText("Track 24").assertIsDisplayed()
        compose.runOnIdle { width = 760.dp }
        compose.onNodeWithText("Track 24").assertIsDisplayed()
        compose.runOnIdle { showAlbum = false }
        compose.runOnIdle {
            assertNotNull(savedViewState.anchorEntryKey)
            assertTrue(savedViewState.fallbackItemIndex > 1)
            showAlbum = true
        }
        val savedTrackNumber = savedViewState.anchorEntryKey!!.substringAfterLast('-').toInt()
        compose.onNodeWithText("Track $savedTrackNumber").assertIsDisplayed()
    }

    @Test
    fun emptyAlbumDisablesActionsAndShowsEmptyStateInWideLayout() {
        setAlbumContent(width = 900.dp, state = albumState(trackCount = 0))

        compose.onNodeWithTag("album-action-play-all").assertIsNotEnabled()
        compose.onNodeWithTag("album-action-shuffle").assertIsNotEnabled()
        compose.onNodeWithTag("album-action-queue-all").assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.album_no_playable_tracks_title)).assertIsDisplayed()
    }

    @Test
    fun navigationLogoClipsItsBackgroundToRoundedCorners() {
        compose.setContent {
            Box(Modifier.size(54.dp).background(Color.Magenta).testTag("logo-background")) {
                AppIcon(54.dp)
            }
        }

        compose.waitUntil(10_000) { compose.onNodeWithTag("logo-background").isDisplayed() }
        val image = compose.onNodeWithTag("logo-background").captureToImage().asAndroidBitmap()
        assertEquals(Color.Magenta.toArgb(), image.getPixel(1, 1))
        assertNotEquals(Color.Magenta.toArgb(), image.getPixel(image.width / 2, image.height / 2))
    }

    private fun setAlbumContent(
        width: Dp,
        height: Dp = 650.dp,
        state: LibraryUiState,
        actions: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport {
                    AlbumDetailScreen(
                        state = state,
                        modifier = Modifier.requiredSize(width, height),
                        pageKey = pageKey,
                        initialViewState = BrowseViewState(),
                        onSaveBrowseViewState = { _, _ -> },
                        onResolveAlbumArtwork = { _, _ -> },
                        onPlay = { actions.add("play:${it.id}") },
                        onQueue = { actions.add("queue:${it.id}") },
                        onPlayAll = { actions.add("play-all:${it.size}") },
                        onShufflePlay = { actions.add("shuffle:${it.size}") },
                        onQueueAll = { actions.add("queue-all:${it.size}") },
                        bottomContentPadding = 112.dp,
                    )
                }
            }
        }
        compose.waitUntil(10_000) { compose.onNodeWithText("Test album").isDisplayed() }
    }

    @Composable
    private fun TestViewport(content: @Composable () -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val deviceDensity = LocalDensity.current
            // Fit the same virtual viewport on both phones and unfolded screens.
            val scale = minOf(1f, maxWidth.value / 900f, maxHeight.value / 780f)
            val testDensity = remember(deviceDensity, scale) {
                Density(deviceDensity.density * scale, deviceDensity.fontScale)
            }
            CompositionLocalProvider(LocalDensity provides testDensity, content = content)
        }
    }

    private fun albumState(trackCount: Int = 30): LibraryUiState = LibraryUiState(
        entries = List(trackCount) { index ->
            MediaEntry(
                id = "track-${index + 1}",
                parentId = "album",
                title = "Track ${index + 1}",
                creator = "Track artist",
                isContainer = false,
            )
        },
        albumSort = AlbumSort.SERVER_DEFAULT,
        path = listOf(
            BrowseLocation("root", "Library"),
            BrowseLocation("album", "Test album", albumArtist = "Album artist", pageKind = LibraryPageKind.ALBUM),
        ),
        browsePageKey = pageKey,
        browseViewState = BrowseViewState(),
        preferences = AppPreferences(),
        isSearching = false,
        servers = emptyList(),
        selectedServerId = "server",
        browseLoadStatus = BrowseLoadStatus.LOADED,
        albumArtwork = null,
        currentTrackId = null,
        playbackState = RemotePlaybackState.STOPPED,
    )

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        val pageKey = BrowsePageKey("server", "album")
    }
}
