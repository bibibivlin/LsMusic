package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBackUnconditionally
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class LibraryLayoutUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    private val showDirectory = mutableStateOf(true)
    private var savedViewState = BrowseViewState()

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

        compose.onNodeWithTag("library-floating-search-input").assertIsDisplayed()
    }

    @Test
    fun gridFastScrollerKeepsFloatingSearchUsableWithHundredsOfAlbums() {
        setLibraryContent()
        compose.onNodeWithContentDescription(text(R.string.switch_to_grid)).performClick()

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
        compose.onNodeWithTag("library-floating-search-input").assertIsDisplayed()
    }

    @Test
    fun nearbySearchReturnsToTheOriginalField() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(3)
        compose.onNodeWithTag("library-floating-search-button").performClick()

        compose.onNodeWithTag("library-inline-search-input").assertIsDisplayed()
        compose.onNodeWithTag("library-floating-search-input").assertDoesNotExist()
    }

    @Test
    fun nearbyGridSearchAlsoReturnsToTheOriginalField() {
        setLibraryContent()
        compose.onNodeWithContentDescription(text(R.string.switch_to_grid)).performClick()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(5)
        compose.onNodeWithTag("library-floating-search-button").performClick()

        compose.onNodeWithTag("library-inline-search-input").assertIsDisplayed()
        compose.onNodeWithTag("library-floating-search-input").assertDoesNotExist()
    }

    @Test
    fun distantSearchKeepsPositionAndClearRestoresItInListAndGrid() {
        setLibraryContent()
        verifyFilteringAndRestoration("Album 200")
        compose.onNodeWithTag("library-close-floating-search").performClick()

        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(0)
        compose.onNodeWithContentDescription(text(R.string.switch_to_grid)).performClick()
        verifyFilteringAndRestoration("Album 200")
    }

    private fun verifyFilteringAndRestoration(anchorTitle: String) {
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        val before = compose.onNodeWithText(anchorTitle).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").assertIsDisplayed()
        val afterOpening = compose.onNodeWithText(anchorTitle).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, afterOpening.top, 1f)

        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 39")
        compose.runOnIdle {
            assertEquals("media:albums:album-200", savedViewState.unfilteredPosition?.anchorEntryKey)
        }
        compose.onNodeWithTag("library-card:album-39").assertIsDisplayed()
        val firstResult = compose.onNodeWithTag("library-card:album-39").fetchSemanticsNode().boundsInRoot
        assertTrue("The first result must stay below the floating search field", firstResult.top >= searchBounds().bottom)
        compose.onNodeWithText(anchorTitle).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.clear_search)).performClick()
        val afterClearing = compose.onNodeWithText(anchorTitle).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, afterClearing.top, 1f)
    }

    @Test
    fun noMatchesCanBeReplacedAndClearedWithoutLosingTheOriginalPosition() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        val before = compose.onNodeWithText("Album 200").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").performTextInput("no such album")
        compose.onNodeWithText(text(R.string.no_matching_music_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.clear_search)).performClick()
        assertEquals(before.top, compose.onNodeWithText("Album 200").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 39")
        compose.onNodeWithTag("library-card:album-39").assertIsDisplayed()
    }

    @Test
    fun backClosesFloatingSearchAndRetainsTheFilter() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 39")
        compose.onNodeWithTag("library-floating-search-input").performImeAction()
        pressBackUnconditionally()

        compose.onNodeWithTag("library-floating-search-input").assertDoesNotExist()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(0)
        compose.onNodeWithTag("library-inline-search-input").assertTextContains("Album 39")
        compose.onNodeWithTag("library-card:album-39").assertIsDisplayed()
    }

    @Test
    fun openingAnAlbumPreservesThePositionToRestoreAfterClearingSearch() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 39")
        compose.onNodeWithTag("library-card:album-39").performClick()
        compose.runOnIdle { showDirectory.value = true }

        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(0)
        compose.onNodeWithTag("library-inline-search-input").assertTextContains("Album 39")
        compose.onNodeWithContentDescription(text(R.string.clear_search)).performClick()
        compose.onNodeWithText("Album 201").assertIsDisplayed()
    }

    @Test
    fun scrollingToTopWhileSearchingKeepsOneFieldAndSmoothlyReturnsToInlineSearch() {
        setLibraryContent()
        val originalSearch = inlineSearchBounds()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 3")
        val expanded = searchBounds()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(0)
        compose.onNodeWithTag("library-floating-search-input").assertIsDisplayed()
        compose.onNodeWithTag("library-floating-search-input").assertTextContains("Album 3")
        compose.onNodeWithContentDescription(text(R.string.clear_search)).assertIsDisplayed()
        compose.onNodeWithTag("library-inline-search-input").assertDoesNotExist()
        assertEquals(expanded.right, searchBounds().right, 1f)
        assertEquals(originalSearch.top, searchBounds().top, 1f)
        assertEquals(originalSearch.left, searchBounds().left, 1f)
        assertEquals(originalSearch.right, searchBounds().right, 1f)
        val title = compose.onNodeWithText(text(R.string.app_name)).fetchSemanticsNode().boundsInRoot
        assertTrue("The search field must not cover the title", searchBounds().top >= title.bottom)
        saveSearchFrame("top-aligned")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-close-floating-search").performClick()
        compose.mainClock.advanceTimeBy(96)
        assertTrue(searchBounds().width < expanded.width)
        assertEquals(originalSearch.top, searchBounds().top, 1f)
        compose.onNodeWithTag("library-inline-search-input").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("library-floating-search-input").assertDoesNotExist()
        compose.onNodeWithTag("library-inline-search-input").assertIsDisplayed().assertTextContains("Album 3")
    }

    @Test
    fun floatingSearchFollowsItsOriginalSlotWhileDraggingInBothDirections() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        val pinnedTop = searchBounds().top
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.onNodeWithTag("library-floating-search-input").performTextInput("Album 3")
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(1)

        val list = compose.onNodeWithTag("library-directory-entries")
        val startTop = searchBounds().top
        var furthestTop = startTop
        repeat(4) {
            list.performTouchInput {
                val start = Offset(centerX, height * .65f)
                swipe(start, start + Offset(0f, 60f), durationMillis = 400L)
            }
            assertEquals(maxOf(pinnedTop, inlineSearchBounds().top), searchBounds().top, 1f)
            furthestTop = maxOf(furthestTop, searchBounds().top)
        }
        assertTrue("The floating field should move with the returning search slot", furthestTop > startTop + 5f)
        repeat(4) {
            list.performTouchInput {
                val start = Offset(centerX, height * .65f)
                swipe(start, start + Offset(0f, -60f), durationMillis = 400L)
            }
            assertEquals(maxOf(pinnedTop, inlineSearchBounds().top), searchBounds().top, 1f)
        }
        assertTrue("The floating field should move back toward its pinned position", searchBounds().top < furthestTop - 5f)
    }

    @Test
    fun nearbyReturnUsesAnEasedScrollBeforeFocusing() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(3)
        val start = compose.onNodeWithTag("library-card:album-0").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.mainClock.advanceTimeBy(96)
        val early = compose.onNodeWithTag("library-card:album-0").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(112)
        val middle = compose.onNodeWithTag("library-card:album-0").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(800)
        val end = compose.onNodeWithTag("library-card:album-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The return should start smoothly", early > start && early < middle)
        assertTrue("The return should settle smoothly", middle < end)
        assertTrue("The return should use nonlinear easing", middle - start > (end - start) * .5f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("library-inline-search-input").assertIsDisplayed()
    }

    @Test
    fun floatingSearchMorphsContinuouslyInBothDirections() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        val collapsed = searchBounds()
        saveSearchFrame("collapsed")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.mainClock.advanceTimeBy(96)
        val early = searchBounds()
        saveSearchFrame("opening")
        compose.mainClock.advanceTimeBy(112)
        val middle = searchBounds()
        compose.mainClock.advanceTimeBy(400)
        val expanded = searchBounds()
        saveSearchFrame("expanded")

        assertTrue(early.width > collapsed.width && early.width < middle.width)
        assertTrue(middle.width < expanded.width)
        assertTrue("The expansion should ease nonlinearly", middle.width - collapsed.width > (expanded.width - collapsed.width) * .5f)
        assertTrue(abs(collapsed.right - expanded.right) <= 1f)

        compose.onNodeWithTag("library-close-floating-search").performClick()
        compose.mainClock.advanceTimeBy(112)
        val returning = searchBounds()
        saveSearchFrame("returning")
        assertTrue(returning.width < expanded.width && returning.width > collapsed.width)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(collapsed.width, searchBounds().width, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun interruptingTheMorphReversesFromItsCurrentWidth() {
        setLibraryContent()
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(203)
        val collapsed = searchBounds().width
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-floating-search-button").performClick()
        compose.mainClock.advanceTimeBy(160)
        val opening = searchBounds().width
        compose.onNodeWithTag("library-close-floating-search").performClick()
        compose.mainClock.advanceTimeByFrame()
        val reversal = searchBounds().width
        assertTrue("The reversal must start at the current size", abs(reversal - opening) < (opening - collapsed) * .2f)
        compose.mainClock.advanceTimeBy(80)
        assertTrue(searchBounds().width < opening)
        compose.onNodeWithTag("library-floating-search-button").performClick()
        val reopening = searchBounds().width
        compose.mainClock.advanceTimeBy(160)
        assertTrue(searchBounds().width > reopening)
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("library-floating-search-input").assertIsDisplayed()
    }

    private fun searchBounds() = compose.onNodeWithTag("library-floating-search").fetchSemanticsNode().boundsInRoot

    private fun inlineSearchBounds() = compose.onNodeWithTag("library-inline-search-slot").fetchSemanticsNode().boundsInRoot

    private fun saveSearchFrame(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureSearchFrames") != "true") return
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "search-transition-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag("library-screenshot-root").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun setLibraryContent() {
        val pageKey = BrowsePageKey("server", "albums")
        val viewState = BrowseViewState(useGrid = false)
        savedViewState = viewState
        val server = DlnaDevice(
            id = "server",
            name = "Test library",
            manufacturer = "Test",
            model = "Server",
            kind = DlnaDeviceKind.MEDIA_SERVER,
        )
        val state = LibraryUiState(
            entries = List(400) { index ->
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
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showDirectory.value) LibraryDirectoryScreen(
                        state = state,
                        modifier = Modifier.requiredSize(width = 412.dp, height = 700.dp).testTag("library-screenshot-root"),
                        pageKey = pageKey,
                        initialViewState = savedViewState,
                        onOpen = { showDirectory.value = false },
                        onNavigateTo = {},
                        onPlay = {},
                        onQueue = {},
                        onPlayAll = {},
                        onShufflePlay = {},
                        onQueueAll = {},
                        onAlbumSort = {},
                        onSaveBrowseViewState = { _, view -> savedViewState = view },
                        bottomContentPadding = 32.dp,
                        onOpenSettings = {},
                    )
                }
            }
        }
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
