package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Offline fixtures exercise real library layouts; no discovery, playback or HTTP is started. */
class AlbumNavigationUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val savedPages = mutableMapOf<BrowsePageKey, BrowseViewState>()
    private lateinit var directory: LibraryUiState
    private val currentPage = mutableStateOf<LibraryUiState?>(null)
    private val artworkResolutions = mutableListOf<BrowsePageKey>()
    private var artworkFile: File? = null
    private val artworkColor = android.graphics.Color.rgb(11, 223, 183)

    @After
    fun removeArtworkFixture() {
        artworkFile?.delete()
    }

    @Test
    fun gridCoverMovesContinuouslyAndReturnsToItsScrolledCard() {
        setLibrary(useGrid = true)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        waitForPaintedCover()
        val original = albumRowBounds("Album 12")
        val originalPaint = requireNotNull(paintedCoverBounds())
        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(160)
        // Shared elements draw in an overlay; semantics report the stationary layout placeholder.
        // Inspect the rendered cover itself, so an ordinary crossfade cannot satisfy this test.
        val moving = requireNotNull(paintedCoverBounds())
        compose.mainClock.advanceTimeBy(160)
        val later = requireNotNull(paintedCoverBounds())
        compose.mainClock.advanceTimeBy(650)
        val expanded = requireNotNull(paintedCoverBounds())
        assertTrue("Cover should have an intermediate width: $originalPaint -> $moving -> $expanded", moving.width > originalPaint.width + 1f)
        assertTrue("Cover should continue growing: $moving -> $later -> $expanded", moving.width < later.width - 1f)
        assertTrue("Cover must not jump directly to its final width: $later -> $expanded", later.width < expanded.width - 1f)
        assertSquare(moving)
        assertSquare(later)

        returnToDirectory()
        compose.mainClock.advanceTimeBy(160)
        val returning = requireNotNull(paintedCoverBounds())
        assertTrue("Cover should shrink on return: $expanded -> $returning -> $originalPaint", returning.width < expanded.width - 1f)
        assertTrue("Cover should still be in flight: $expanded -> $returning -> $originalPaint", returning.width > originalPaint.width + 1f)
        assertSquare(returning)
        compose.mainClock.advanceTimeBy(650)
        assertSameBounds(originalPaint, requireNotNull(paintedCoverBounds()))
        assertSameBounds(original, albumRowBounds("Album 12"))
    }

    @Test
    fun compactGridCoverKeepsItsCornersThroughBothTransitions() {
        assertCoverCornersRemainStable(useGrid = true, gallerySize = GallerySize.COMPACT)
    }

    @Test
    fun standardGridCoverKeepsItsCornersThroughBothTransitions() {
        assertCoverCornersRemainStable(useGrid = true, gallerySize = GallerySize.STANDARD)
    }

    @Test
    fun largeGridCoverKeepsItsCornersThroughBothTransitions() {
        assertCoverCornersRemainStable(useGrid = true, gallerySize = GallerySize.LARGE)
    }

    @Test
    fun listCoverKeepsItsCornersThroughBothTransitions() {
        assertCoverCornersRemainStable(useGrid = false, gallerySize = GallerySize.STANDARD)
    }

    private fun assertCoverCornersRemainStable(useGrid: Boolean, gallerySize: GallerySize) {
        setLibrary(useGrid = useGrid, gallerySize = gallerySize)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        waitForPaintedCover()
        val initial = requireNotNull(paintedCoverGeometry())

        openAlbum("album-12")
        val openingStart = requireNotNull(paintedCoverGeometry())
        compose.mainClock.advanceTimeBy(160)
        val opening = requireNotNull(paintedCoverGeometry())
        compose.mainClock.advanceTimeBy(650)
        val expanded = requireNotNull(paintedCoverGeometry())
        assertSameCorners(initial, openingStart, opening, expanded)

        returnToDirectory()
        val returningStart = requireNotNull(paintedCoverGeometry())
        compose.mainClock.advanceTimeBy(160)
        val returning = requireNotNull(paintedCoverGeometry())
        compose.mainClock.advanceTimeBy(650)
        val restored = requireNotNull(paintedCoverGeometry())
        assertSameCorners(initial, returningStart, returning, restored)
        assertSameBounds(initial.bounds, restored.bounds)
    }

    @Test
    fun slowBrowseDoesNotDelayTheCoverAndLoadingCompletionDoesNotReplayIt() {
        setLibrary(useGrid = false)
        openAlbum("album-0")
        compose.mainClock.advanceTimeBy(650)
        val waitingCover = requireNotNull(paintedCoverBounds())
        compose.runOnIdle {
            assertEquals(BrowseLoadStatus.LOADING, currentPage.value!!.browseLoadStatus)
            assertTrue(artworkResolutions.isEmpty())
            currentPage.value = currentPage.value!!.copy(
                entries = tracks("album-0"),
                browseLoadStatus = BrowseLoadStatus.LOADED,
            )
        }
        compose.mainClock.advanceTimeBy(120)
        assertSameBounds(waitingCover, requireNotNull(paintedCoverBounds()))
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        compose.runOnIdle { assertTrue(artworkResolutions.contains(BrowsePageKey("server", "album-0"))) }
    }

    @Test
    fun quickBackDuringListCoverFlightPreservesTheViewportAndAllowsReopening() {
        setLibrary(useGrid = false)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        waitForPaintedCover()
        val original = albumRowBounds("Album 12")
        val originalPaint = requireNotNull(paintedCoverBounds())
        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(80)
        val moving = requireNotNull(paintedCoverBounds())
        assertTrue("List cover should begin growing: $originalPaint -> $moving", moving.width > originalPaint.width + 1f)
        assertSquare(moving)
        returnToDirectory()
        compose.mainClock.advanceTimeBy(650)
        // Synchronize with a rendered frame before checking semantics after an interrupted flight.
        assertSameBounds(originalPaint, requireNotNull(paintedCoverBounds()))
        assertSameBounds(original, albumRowBounds("Album 12"))
        compose.onNodeWithText("Album 12").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(650)
        val reopened = requireNotNull(paintedCoverBounds())
        assertTrue("Reopening should show the expanded cover", reopened.width > originalPaint.width + 1f)
        compose.onNodeWithText("Album 12").assertIsDisplayed()
        compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun wideLayoutKeepsTheCoverInFlightWhenTracksArriveAndReturnsToTheSameCard() {
        setLibrary(useGrid = true, viewportWidth = 900.dp, viewportHeight = 650.dp)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        waitForPaintedCover()
        val original = albumRowBounds("Album 12")
        val originalPaint = requireNotNull(paintedCoverBounds())
        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            currentPage.value = currentPage.value!!.copy(
                entries = tracks("album-12"),
                browseLoadStatus = BrowseLoadStatus.LOADED,
            )
        }
        compose.mainClock.advanceTimeBy(80)
        val moving = requireNotNull(paintedCoverBounds())
        compose.mainClock.advanceTimeBy(650)
        val expanded = requireNotNull(paintedCoverBounds())
        assertTrue("Cover should grow in the wide layout: $originalPaint -> $moving -> $expanded", moving.width > originalPaint.width + 1f)
        assertTrue("Loading tracks should not skip the flight: $moving -> $expanded", moving.width < expanded.width - 1f)
        assertSquare(moving)
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        val viewport = coverBounds("album-navigation-root")
        assertTrue("Wide layout should keep the cover on the left", expanded.right < viewport.width / 2f)

        returnToDirectory()
        compose.mainClock.advanceTimeBy(650)
        assertSameBounds(originalPaint, requireNotNull(paintedCoverBounds()))
        assertSameBounds(original, albumRowBounds("Album 12"))
    }

    private fun setLibrary(
        useGrid: Boolean,
        gallerySize: GallerySize = GallerySize.STANDARD,
        viewportWidth: Dp? = null,
        viewportHeight: Dp = 650.dp,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("album-navigation-", ".png", context.cacheDir)
        artworkFile = file
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(artworkColor)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val server = DlnaDevice("server", "Offline library", "Test", "Server", DlnaDeviceKind.MEDIA_SERVER)
        directory = LibraryUiState(
            entries = List(400) { index ->
                MediaEntry(
                    id = "album-$index", parentId = "albums", title = "Album $index",
                    creator = "Artist", albumArtist = "Artist", isContainer = true, isAlbum = true,
                    artworkUri = if (index == 0 || index == 12) file.toURI().toString() else null,
                )
            },
            albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("albums", "Albums")),
            browsePageKey = BrowsePageKey(server.id, "albums"),
            browseViewState = BrowseViewState(useGrid = useGrid),
            preferences = AppPreferences(useGridByDefault = useGrid, gallerySize = gallerySize),
            isSearching = false, servers = listOf(server), selectedServerId = server.id,
            browseLoadStatus = BrowseLoadStatus.LOADED, albumArtwork = null,
            currentTrackId = null, playbackState = RemotePlaybackState.STOPPED,
        )
        currentPage.value = directory
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport(viewportWidth, viewportHeight) {
                    LibraryScreen(
                        state = currentPage.value!!,
                        onOpen = { entry ->
                            currentPage.value = directory.copy(
                                entries = emptyList(),
                                path = directory.path + BrowseLocation(
                                    entry.id, entry.title, artworkUri = entry.artworkUri, albumArtist = entry.albumArtist,
                                    pageKind = LibraryPageKind.ALBUM,
                                ),
                                browsePageKey = BrowsePageKey("server", entry.id),
                                browseViewState = savedPages[BrowsePageKey("server", entry.id)] ?: BrowseViewState(),
                                browseLoadStatus = BrowseLoadStatus.LOADING,
                            )
                        },
                        onNavigateTo = {}, onPlay = {}, onQueue = {}, onPlayAll = {},
                        onShufflePlay = {}, onQueueAll = {}, onAlbumSort = {},
                        onSaveBrowseViewState = { page, view -> savedPages[page] = view },
                        onResolveAlbumArtwork = { page, _ -> artworkResolutions += page },
                        bottomContentPadding = 32.dp, onOpenSettings = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun TestViewport(width: Dp?, height: Dp, content: @Composable () -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val deviceDensity = LocalDensity.current
            val scale = if (width == null) 1f else minOf(1f, maxWidth / width, maxHeight / height)
            val testDensity = remember(deviceDensity, scale) {
                Density(deviceDensity.density * scale, deviceDensity.fontScale)
            }
            CompositionLocalProvider(LocalDensity provides testDensity) {
                val size = if (width == null) Modifier.fillMaxSize() else Modifier.requiredSize(width, height)
                Box(size.testTag("album-navigation-root")) { content() }
            }
        }
    }

    private fun openAlbum(id: String) {
        waitForPaintedCover()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-cover:$id", useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun returnToDirectory() {
        compose.runOnIdle {
            currentPage.value = directory.copy(browseViewState = savedPages[directory.pageKey] ?: BrowseViewState())
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun coverBounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun albumRowBounds(title: String): Rect =
        compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private fun waitForPaintedCover() {
        compose.waitUntil(10_000) { paintedCoverBounds() != null }
    }

    private data class CoverGeometry(
        val bounds: Rect,
        val topLeftInset: Int,
        val topRightInset: Int,
        val bottomLeftInset: Int,
        val bottomRightInset: Int,
    )

    private fun paintedCoverBounds(): Rect? = paintedCoverGeometry()?.bounds

    private fun paintedCoverGeometry(): CoverGeometry? {
        val bitmap = compose.onNodeWithTag("album-navigation-root")
            .captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (isArtworkColor(color)) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        val sampleInset = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .displayMetrics.density.roundToInt().coerceAtLeast(1)
        fun cornerInsets(y: Int): Pair<Int, Int> {
            val row = y.coerceIn(top, bottom) * bitmap.width
            val first = (left..right).firstOrNull { x -> isArtworkColor(pixels[row + x]) }
                ?: error("No cover pixels on row $y")
            val last = (right downTo left).first { x -> isArtworkColor(pixels[row + x]) }
            return (first - left) to (right - last)
        }
        val (topLeft, topRight) = cornerInsets(top + sampleInset)
        val (bottomLeft, bottomRight) = cornerInsets(bottom - sampleInset)
        return CoverGeometry(
            bounds = Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f),
            topLeftInset = topLeft,
            topRightInset = topRight,
            bottomLeftInset = bottomLeft,
            bottomRightInset = bottomRight,
        )
    }

    private fun isArtworkColor(color: Int): Boolean =
        // Device color conversion can shift a channel by a few levels when scaling a bitmap.
        abs(android.graphics.Color.red(color) - android.graphics.Color.red(artworkColor)) <= 4 &&
            abs(android.graphics.Color.green(color) - android.graphics.Color.green(artworkColor)) <= 4 &&
            abs(android.graphics.Color.blue(color) - android.graphics.Color.blue(artworkColor)) <= 4

    private fun assertSameCorners(expected: CoverGeometry, vararg actual: CoverGeometry) {
        actual.forEach {
            val differences = listOf(
                abs(expected.topLeftInset - it.topLeftInset),
                abs(expected.topRightInset - it.topRightInset),
                abs(expected.bottomLeftInset - it.bottomLeftInset),
                abs(expected.bottomRightInset - it.bottomRightInset),
            )
            assertTrue("Cover corners should not jump: $expected -> $it", differences.all { difference -> difference <= 3 })
        }
    }

    private fun assertSameBounds(expected: Rect, actual: Rect) {
        assertTrue("Cover should return to $expected, was $actual", abs(expected.left - actual.left) < 2f)
        assertTrue("Cover should return to $expected, was $actual", abs(expected.top - actual.top) < 2f)
        assertTrue("Cover size should remain stable", abs(expected.width - actual.width) < 2f)
    }

    private fun assertSquare(bounds: Rect) {
        assertTrue("The cover must not be cropped during the flight: $bounds", abs(bounds.width - bounds.height) < 3f)
    }

    private fun tracks(albumId: String) = List(12) { index ->
        MediaEntry("track-$index", albumId, "Track ${index + 1}", creator = "Artist", isContainer = false)
    }
}
