package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
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
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt

/** Offline fixtures exercise real library layouts; no discovery, playback or HTTP is started. */
class AlbumNavigationUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    private val savedPages = mutableMapOf<BrowsePageKey, BrowseViewState>()
    private lateinit var directory: LibraryUiState
    private val currentPage = mutableStateOf<LibraryUiState?>(null)
    private val artworkResolutions = mutableListOf<BrowsePageKey>()
    private var artworkFile: File? = null
    private var secondArtworkFile: File? = null
    private var thirdArtworkFile: File? = null
    private val artworkColor = android.graphics.Color.rgb(11, 223, 183)
    private val secondArtworkColor = android.graphics.Color.rgb(239, 71, 112)
    private val thirdArtworkColor = android.graphics.Color.rgb(58, 91, 237)

    @After
    fun removeArtworkFixture() {
        artworkFile?.delete()
        secondArtworkFile?.delete()
        thirdArtworkFile?.delete()
    }

    @Test
    fun gridCoverMovesContinuouslyAndReturnsToItsScrolledCard() {
        setLibrary(useGrid = true)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        waitForPaintedCover()
        val original = albumRowBounds("Album 12")
        val originalPaint = requireNotNull(paintedCoverBounds())
        saveFrame("grid-source")
        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(160)
        // Shared elements draw in an overlay; semantics report the stationary layout placeholder.
        // Inspect the rendered cover itself, so an ordinary crossfade cannot satisfy this test.
        val moving = requireNotNull(paintedCoverBounds())
        saveFrame("grid-flight")
        compose.mainClock.advanceTimeBy(160)
        val later = requireNotNull(paintedCoverBounds())
        compose.mainClock.advanceTimeBy(160)
        val overshoot = requireNotNull(paintedCoverBounds())
        saveFrame("grid-overshoot")
        compose.mainClock.advanceTimeBy(650)
        val expanded = requireNotNull(paintedCoverBounds())
        saveFrame("grid-expanded")
        assertTrue("The cover should gently overshoot before settling", overshoot.width > expanded.width)
        assertTrue("The overshoot must remain subtle", overshoot.width <= expanded.width +
            (expanded.width - originalPaint.width) * .01f + 2f)
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
    fun partiallyScrolledCoverRemainsBelowSearchAndProtectedStatusArea() {
        setLibrary(useGrid = true, gallerySize = GallerySize.LARGE, statusInset = 24.dp, usePlayerTransition = true)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        compose.onNodeWithTag("library-directory-entries").performTouchInput {
            val start = Offset(centerX, height * .7f)
            swipe(start, start + Offset(0f, -120f), durationMillis = 400L)
        }
        waitForPaintedCover()
        val search = compose.onNodeWithTag("library-floating-search").fetchSemanticsNode().boundsInRoot
        val root = coverBounds("album-navigation-root")
        val source = coverBounds("library-cover:album-12")
        val statusBottom = root.top + with(compose.density) { 24.dp.toPx() }
        assertTrue("The test cover should extend behind the status bar", source.top < statusBottom)
        assertTrue("The test cover should be underneath the floating search", source.overlaps(search))
        val sampleX = (search.left - root.left + search.width * .22f).roundToInt()
        val sampleY = (search.top - root.top + search.height * .5f).roundToInt()
        val before = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
        val statusX = (source.center.x - root.left).roundToInt().coerceIn(0, before.width - 1)
        val statusY = with(compose.density) { 8.dp.roundToPx() }.coerceIn(0, before.height - 1)
        assertTrue("Artwork behind status icons must be protected", !isArtworkColor(before.getPixel(statusX, statusY)))
        val originalSearchPixel = before.getPixel(sampleX, sampleY)
        assertTrue("Search must initially cover the album", !isArtworkColor(originalSearchPixel))

        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(48)
        val opening = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
        assertTrue("The flying cover must not bypass status protection", !isArtworkColor(opening.getPixel(statusX, statusY)))
        assertTrue("The flying cover must remain below the fading search control", !isArtworkColor(opening.getPixel(sampleX, sampleY)))
        saveFrame("edge-opening")
        returnToDirectory()
        compose.mainClock.advanceTimeByFrame()
        val reversed = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
        assertTrue("An interrupted flight must keep the status area protected", !isArtworkColor(reversed.getPixel(statusX, statusY)))
        compose.mainClock.advanceTimeBy(800)
        assertSameBounds(source, coverBounds("library-cover:album-12"))

        openAlbum("album-12")
        compose.mainClock.advanceTimeBy(800)
        returnToDirectory()
        compose.mainClock.advanceTimeBy(160)
        val returning = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
        assertTrue("The returning cover must remain below the appearing search control", !isArtworkColor(returning.getPixel(sampleX, sampleY)))
        saveFrame("edge-returning")
        compose.mainClock.advanceTimeBy(800)
        assertSameBounds(search, compose.onNodeWithTag("library-floating-search").fetchSemanticsNode().boundsInRoot)
        assertSameBounds(source, coverBounds("library-cover:album-12"))
    }

    @Test
    fun movingCoverStaysProtectedBehindStatusIconsThroughoutTheTransition() {
        setLibrary(useGrid = true, gallerySize = GallerySize.LARGE, statusInset = 24.dp, usePlayerTransition = true)
        compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        compose.onNodeWithTag("library-directory-entries").performTouchInput {
            val start = Offset(centerX, height * .7f)
            swipe(start, start + Offset(0f, -210f), durationMillis = 400L)
        }
        waitForPaintedCover()
        val root = coverBounds("album-navigation-root")
        val source = coverBounds("library-cover:album-12")
        assertTrue("The fixture should start behind the status bar: source=$source root=$root", source.top <= root.top)
        val statusHeight = with(compose.density) { 24.dp.roundToPx() }

        fun assertStatusProtected(phase: String) {
            val frame = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
            var exposed = 0
            for (y in 0 until statusHeight.coerceAtMost(frame.height)) {
                for (x in 0 until frame.width) {
                    if (isArtworkColor(frame.getPixel(x, y))) exposed++
                }
            }
            assertEquals("Artwork must remain protected behind status icons at $phase", 0, exposed)
        }

        assertStatusProtected("source")
        openAlbum("album-12")
        var previous = 0L
        for (time in listOf(48L, 112L, 176L, 240L, 320L, 480L)) {
            compose.mainClock.advanceTimeBy(time - previous)
            previous = time
            assertStatusProtected("opening ${time}ms")
        }
        compose.mainClock.advanceTimeBy(400)
        returnToDirectory()
        previous = 0L
        for (time in listOf(48L, 112L, 176L, 240L, 320L, 480L)) {
            compose.mainClock.advanceTimeBy(time - previous)
            previous = time
            assertStatusProtected("returning ${time}ms")
        }
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

    @Test
    fun pressingAlbumScalesTheWholeCardAndWaitsForRelease() {
        setLibrary(useGrid = true)
        waitForPaintedCover()
        val cover = requireNotNull(paintedCoverBounds())
        val title = compose.onNodeWithText("Album 0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(directory.pageKey, currentPage.value!!.pageKey)
        val pressed = requireNotNull(paintedCoverBounds())
        val pressedTitle = compose.onNodeWithText("Album 0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(.96f, pressed.width / cover.width, .02f)
        assertEquals(.96f, pressedTitle.width / title.width, .01f)
        saveFrame("grid-pressed")
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput { up() }
        assertEquals("Release should start navigation immediately", "album-0", currentPage.value!!.pageKey.objectId)
        compose.mainClock.advanceTimeUntil(500) { currentPage.value!!.pageKey != directory.pageKey }
        compose.mainClock.advanceTimeByFrame()
        assertSameBounds(pressed, requireNotNull(paintedCoverBounds()))
        var previousWidth = pressed.width
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            val width = requireNotNull(paintedCoverBounds()).width
            assertTrue("The released cover must keep growing: $previousWidth -> $width", width >= previousWidth - 1f)
            previousWidth = width
        }
        assertTrue("The cover should already be flying", previousWidth > cover.width + 5f)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun scrollingCancelsAlbumPressWithoutOpeningIt() {
        setLibrary(useGrid = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(0f, -200f))
            up()
        }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(directory.pageKey, currentPage.value!!.pageKey)
        compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun listAlbumFliesFromItsPressedSize() {
        setLibrary(useGrid = false)
        waitForPaintedCover()
        val original = requireNotNull(paintedCoverBounds())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        val pressed = requireNotNull(paintedCoverBounds())
        assertTrue("The whole row should shrink its cover", pressed.width < original.width - 1f)
        compose.onNodeWithTag("library-cover:album-0", useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        assertSameBounds(pressed, requireNotNull(paintedCoverBounds()))
        compose.mainClock.advanceTimeBy(160)
        assertTrue("The cover should grow directly from the pressed row", requireNotNull(paintedCoverBounds()).width > original.width + 5f)
    }

    private fun assertCoverCornersRemainStable(useGrid: Boolean, gallerySize: GallerySize) {
        setLibrary(useGrid = useGrid, gallerySize = gallerySize)
        // The floating search button occludes the upper-right corner of a full-width scrolled card.
        // Inspect that layout below the original search field; scrolled return is covered separately.
        val albumId = if (gallerySize == GallerySize.LARGE) "album-0" else "album-12"
        if (gallerySize != GallerySize.LARGE) {
            compose.onNodeWithTag("library-directory-entries").performScrollToIndex(15)
        }
        waitForPaintedCover()
        val initial = requireNotNull(paintedCoverGeometry())

        openAlbum(albumId)
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

    @OptIn(DelicateCoilApi::class)
    @Test
    fun coldAlbumUsesCachedThumbnailDuringFlightAndLoadsLargeArtworkAfterLanding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLoader = SingletonImageLoader.get(context)
        val detailRequests = Collections.synchronizedList(mutableListOf<String>())
        val loadedDetails = Collections.synchronizedList(mutableListOf<String>())
        val loader = ImageLoader.Builder(context)
            .eventListener(object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    if (request.memoryCacheKey == null) detailRequests.add(request.data.toString())
                }
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    if (request.memoryCacheKey == null) loadedDetails.add(request.data.toString())
                }
            })
            .diskCache(null)
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            setLibrary(useGrid = true, artworkSize = 2048)
            waitForPaintedCover()
            val uri = requireNotNull(artworkFile).toURI().toString()
            detailRequests.clear()
            openAlbum("album-0")
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            assertTrue("A cold full-size request must not start during the cover flight", uri !in detailRequests)
            assertTrue("The flying cover should retain the album thumbnail", paintedCoverBounds() != null)
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitUntil(5_000) { uri in loadedDetails }

            returnToDirectory()
            compose.mainClock.advanceTimeBy(1_000)
            waitForPaintedCover()
            detailRequests.clear()
            openAlbum("album-0")
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            assertTrue("Reopening should retain the thumbnail throughout the flight", uri !in detailRequests)
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitUntil(5_000) { uri in detailRequests }
        } finally {
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
        }
    }

    @Test
    fun largeArtworkFadesOverTheThumbnailWhenLoadedFromDisk() {
        verifyLargeArtworkTransition(preload = false)
    }

    @Test
    fun largeArtworkFadesOverTheThumbnailWhenAlreadyInMemory() {
        verifyLargeArtworkTransition(preload = true)
    }

    @OptIn(DelicateCoilApi::class)
    private fun verifyLargeArtworkTransition(preload: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalLoader = SingletonImageLoader.get(context)
        val loadedSources = Collections.synchronizedList(mutableListOf<Pair<String, DataSource>>())
        val failedUris = Collections.synchronizedList(mutableListOf<String>())
        val loader = ImageLoader.Builder(context)
            .eventListener(object : EventListener() {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    if (request.memoryCacheKey == null) {
                        loadedSources.add(request.data.toString() to result.dataSource)
                    }
                }
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    failedUris.add(request.data.toString())
                }
            })
            .diskCache(null)
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            setLibrary(useGrid = true, artworkSize = 1024, concurrentArtwork = true)
            val detailUri = requireNotNull(secondArtworkFile).toURI().toString()
            if (preload) {
                val result = runBlocking {
                    loader.execute(
                        ImageRequest.Builder(context)
                            .data(detailUri)
                            .size(1024)
                            .scale(Scale.FILL)
                            .precision(Precision.INEXACT)
                            .build(),
                    )
                }
                assertTrue(result is SuccessResult)
            }
            loadedSources.clear()
            openAlbum("album-0")
            compose.mainClock.advanceTimeBy(1_000)
            val thumbnailUri = requireNotNull(artworkFile).toURI().toString()
            compose.waitUntil(10_000) { synchronized(loadedSources) { loadedSources.any { it.first == thumbnailUri } } }
            compose.mainClock.advanceTimeBy(400)
            assertColorNear(artworkColor, detailCoverCenter())

            compose.runOnIdle {
                currentPage.value = currentPage.value!!.copy(
                    albumArtwork = AlbumArtworkUiState(BrowsePageKey("server", "album-0"), 1024, detailUri),
                )
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(10_000) { synchronized(loadedSources) { loadedSources.any { it.first == detailUri } } }
            val source = synchronized(loadedSources) { loadedSources.last { it.first == detailUri }.second }
            assertEquals("The fixture must exercise the intended cache path", preload, source == DataSource.MEMORY_CACHE)
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(110)
            assertArtworkBlend(artworkColor, secondArtworkColor, detailCoverCenter())
            compose.mainClock.advanceTimeBy(400)
            assertColorNear(secondArtworkColor, detailCoverCenter())

            if (!preload) {
                val missingUri = File(context.cacheDir, "missing-album-${System.nanoTime()}.png").toURI().toString()
                compose.runOnIdle {
                    currentPage.value = currentPage.value!!.copy(
                        albumArtwork = AlbumArtworkUiState(BrowsePageKey("server", "album-0"), 1024, missingUri),
                    )
                }
                compose.mainClock.advanceTimeByFrame()
                compose.waitUntil(10_000) { missingUri in failedUris }
                compose.mainClock.advanceTimeBy(400)
                assertColorNear(secondArtworkColor, detailCoverCenter())
            }
        } finally {
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.shutdown()
        }
    }

    private fun detailCoverCenter(): Int {
        val bitmap = compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        assertTrue("Expected artwork color $expected, was $actual", isArtworkColor(actual, expected))
    }

    private fun assertArtworkBlend(from: Int, to: Int, actual: Int) {
        val red = android.graphics.Color.red(actual)
        val startRed = android.graphics.Color.red(from)
        val endRed = android.graphics.Color.red(to)
        assertTrue("Both artwork images should be visible: $actual", red in (startRed + 8) until (endRed - 8))
        val progress = (red - startRed).toFloat() / (endRed - startRed)
        fun blendedChannel(start: Int, end: Int) = start + (end - start) * progress
        assertTrue(
            "The thumbnail should remain opaque beneath the incoming artwork: $actual",
            abs(android.graphics.Color.green(actual) - blendedChannel(android.graphics.Color.green(from), android.graphics.Color.green(to))) < 8f &&
                abs(android.graphics.Color.blue(actual) - blendedChannel(android.graphics.Color.blue(from), android.graphics.Color.blue(to))) < 8f,
        )
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
    fun quickGridAlbumChangesStartEveryCoverWithoutWaitingForReturn() {
        assertConcurrentAlbumChanges(useGrid = true)
    }

    @Test
    fun quickListAlbumChangesStartEveryCoverWithoutWaitingForReturn() {
        assertConcurrentAlbumChanges(useGrid = false)
    }

    @Test
    fun consecutiveGridAlbumsRemoveEveryPreviousCoverWhenTheLastAlbumSettles() {
        assertPreviousCoversAreRemoved(useGrid = true)
    }

    @Test
    fun consecutiveListAlbumsRemoveEveryPreviousCoverWhenTheLastAlbumSettles() {
        assertPreviousCoversAreRemoved(useGrid = false)
    }

    private fun assertPreviousCoversAreRemoved(useGrid: Boolean) {
        setLibrary(useGrid = useGrid, concurrentArtwork = true)
        compose.waitUntil(10_000) {
            paintedCoverBounds() != null && paintedCoverBounds(secondArtworkColor) != null &&
                paintedCoverBounds(thirdArtworkColor) != null
        }
        repeat(9) { iteration ->
            val index = iteration % 3
            openAlbum("album-$index", waitForArtwork = false)
            compose.runOnIdle {
                currentPage.value = currentPage.value!!.copy(
                    entries = tracks("album-$index"), browseLoadStatus = BrowseLoadStatus.LOADED,
                )
            }
            compose.mainClock.advanceTimeBy(96)
            if (iteration < 8) {
                returnToDirectory()
                compose.mainClock.advanceTimeBy(32)
            }
        }
        // Earlier returns have finished while the last, larger opening spring still runs.
        compose.mainClock.advanceTimeBy(480)
        saveFrame(if (useGrid) "grid-previous-covers-finished" else "list-previous-covers-finished")
        assertOnlyLastCoverIsPainted()
        compose.mainClock.advanceTimeBy(1_000)
        saveFrame(if (useGrid) "grid-settled-no-ghosts" else "list-settled-no-ghosts")
        assertOnlyLastCoverIsPainted()
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("The final album must finish its transition and resolve artwork",
                artworkResolutions.contains(BrowsePageKey("server", "album-2")))
        }
    }

    private fun assertOnlyLastCoverIsPainted() {
        val bitmap = compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertEquals("The first album must not remain above the track list", 0,
            pixels.count { isArtworkColor(it, artworkColor) })
        assertEquals("The second album must not remain above the track list", 0,
            pixels.count { isArtworkColor(it, secondArtworkColor) })
        assertTrue("The last album must retain its own cover", pixels.any { isArtworkColor(it, thirdArtworkColor) })
    }

    private fun assertConcurrentAlbumChanges(useGrid: Boolean) {
        setLibrary(useGrid = useGrid, concurrentArtwork = true)
        waitForPaintedCover()
        compose.waitUntil(10_000) { paintedCoverBounds(secondArtworkColor) != null }
        val sourceBounds = listOf(
            requireNotNull(paintedCoverBounds()),
            requireNotNull(paintedCoverBounds(secondArtworkColor)),
        )
        val movingWidths = mutableListOf<Float>()
        // Never let the cover spring settle between clicks. Alternate real card touch events,
        // including reopening an album whose previous return is still running.
        repeat(6) { iteration ->
            val index = iteration % 2
            val color = if (index == 0) artworkColor else secondArtworkColor
            openAlbum("album-$index", waitForArtwork = false)
            compose.mainClock.advanceTimeBy(96)
            val moving = requireNotNull(paintedCoverBounds(color))
            assertTrue("Album $index must grow on click $iteration: $moving", moving.width > sourceBounds[index].width + 1f)
            movingWidths += moving.width
            if (iteration == 1) {
                saveFrame(if (useGrid) "grid-concurrent-covers" else "list-concurrent-covers")
            }
            returnToDirectory()
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertSameBounds(sourceBounds[0], requireNotNull(paintedCoverBounds()))
        assertSameBounds(sourceBounds[1], requireNotNull(paintedCoverBounds(secondArtworkColor)))
        compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle {
            assertTrue("Abandoned albums must not start full artwork resolution", artworkResolutions.isEmpty())
        }

        openAlbum("album-1")
        compose.mainClock.advanceTimeBy(1_000)
        val expanded = requireNotNull(paintedCoverBounds(secondArtworkColor))
        movingWidths.forEachIndexed { iteration, width ->
            assertTrue("Click $iteration must not skip its flight: $width -> ${expanded.width}", width < expanded.width - 1f)
        }
        compose.onNodeWithTag("album-detail-cover", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            currentPage.value = currentPage.value!!.copy(entries = tracks("album-1"), browseLoadStatus = BrowseLoadStatus.LOADED)
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(artworkResolutions.contains(BrowsePageKey("server", "album-1"))) }

        // Also interrupt a return after the previous album fully settled. Its old page must
        // not become the next animation's source just because its fade has not finished.
        returnToDirectory()
        compose.mainClock.advanceTimeBy(32)
        openAlbum("album-0", waitForArtwork = false)
        compose.mainClock.advanceTimeBy(96)
        val next = requireNotNull(paintedCoverBounds())
        assertTrue("A settled previous album must not suppress the next flight: $next",
            next.width > sourceBounds[0].width + 1f && next.width < expanded.width - 1f)
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
        artworkSize: Int = 64,
        statusInset: Dp = 0.dp,
        usePlayerTransition: Boolean = false,
        concurrentArtwork: Boolean = false,
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("album-navigation-", ".png", context.cacheDir)
        artworkFile = file
        val bitmap = Bitmap.createBitmap(artworkSize, artworkSize, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(artworkColor)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        if (concurrentArtwork) {
            secondArtworkFile = File.createTempFile("album-navigation-second-", ".png", context.cacheDir).also { secondFile ->
                val secondBitmap = Bitmap.createBitmap(artworkSize, artworkSize, Bitmap.Config.ARGB_8888)
                secondBitmap.eraseColor(secondArtworkColor)
                secondFile.outputStream().use { secondBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                secondBitmap.recycle()
            }
            thirdArtworkFile = File.createTempFile("album-navigation-third-", ".png", context.cacheDir).also { thirdFile ->
                val thirdBitmap = Bitmap.createBitmap(artworkSize, artworkSize, Bitmap.Config.ARGB_8888)
                thirdBitmap.eraseColor(thirdArtworkColor)
                thirdFile.outputStream().use { thirdBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                thirdBitmap.recycle()
            }
        }
        val server = DlnaDevice("server", "Offline library", "Test", "Server", DlnaDeviceKind.MEDIA_SERVER)
        directory = LibraryUiState(
            entries = List(400) { index ->
                MediaEntry(
                    id = "album-$index", parentId = "albums", title = "Album $index",
                    creator = "Artist", albumArtist = "Artist", isContainer = true, isAlbum = true,
                    artworkUri = when (index) {
                        0, 12 -> file.toURI().toString()
                        1 -> secondArtworkFile?.toURI()?.toString()
                        2 -> thirdArtworkFile?.toURI()?.toString()
                        else -> null
                    },
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
                    val libraryContent: @Composable () -> Unit = {
                        CompositionLocalProvider(LocalContentTopInset provides statusInset) {
                            TopEdgeProtection(statusInset) {
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
                    if (usePlayerTransition) {
                        PlayerPageTransition(
                            destination = AppDestination.LIBRARY,
                            track = null,
                            miniPlayerEnabled = false,
                        ) { libraryContent() }
                    } else {
                        libraryContent()
                    }
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
                Box(size.background(MaterialTheme.colorScheme.background).testTag("album-navigation-root")) { content() }
            }
        }
    }

    private fun openAlbum(id: String, waitForArtwork: Boolean = true) {
        if (waitForArtwork) waitForPaintedCover()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("library-cover:$id", useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeUntil(500) { currentPage.value!!.pageKey.objectId == id }
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

    private fun paintedCoverBounds(color: Int = artworkColor): Rect? = paintedCoverGeometry(color)?.bounds

    private fun saveFrame(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "album-transition-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag("album-navigation-root").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun paintedCoverGeometry(artwork: Int = artworkColor): CoverGeometry? {
        val bitmap = compose.onNodeWithTag("album-navigation-root")
            .captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (isArtworkColor(color, artwork)) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        // Sample inside the arc, away from its nearly horizontal tangent where a subpixel
        // translation changes the measured edge by several pixels after rasterization.
        val sampleInset = (InstrumentationRegistry.getInstrumentation().targetContext.resources
            .displayMetrics.density * 4f).roundToInt().coerceAtLeast(2)
        fun cornerInsets(y: Int): Pair<Int, Int> {
            val row = y.coerceIn(top, bottom) * bitmap.width
            val first = (left..right).firstOrNull { x -> isArtworkColor(pixels[row + x], artwork) }
                ?: error("No cover pixels on row $y")
            val last = (right downTo left).first { x -> isArtworkColor(pixels[row + x], artwork) }
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

    private fun isArtworkColor(color: Int, artwork: Int = artworkColor): Boolean =
        // Device color conversion can shift a channel by a few levels when scaling a bitmap.
        abs(android.graphics.Color.red(color) - android.graphics.Color.red(artwork)) <= 4 &&
            abs(android.graphics.Color.green(color) - android.graphics.Color.green(artwork)) <= 4 &&
            abs(android.graphics.Color.blue(color) - android.graphics.Color.blue(artwork)) <= 4

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
