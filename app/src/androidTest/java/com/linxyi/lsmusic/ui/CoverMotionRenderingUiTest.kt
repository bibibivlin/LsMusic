@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.linxyi.lsmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverMotionRenderingUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    @Before
    fun keepFixtureVisible() {
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
    }

    @Test
    fun changingPlaybackArtworkDoesNotRecomposeUnrelatedPageContent() {
        var track by mutableStateOf(MediaEntry("track-0", "album", "Offline track", isContainer = false))
        val counts = ContentCounts()
        val motionCounts = ContentCounts()
        compose.setContent {
            PlayerPageTransition(AppDestination.LIBRARY, track = track, miniPlayerEnabled = true) {
                CountedImage(counts)
                CountedPlayerMotion(motionCounts)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { counts.compositions = 0; motionCounts.compositions = 0 }
        repeat(5) { index ->
            compose.runOnIdle { track = track.copy(id = "track-${index + 1}") }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals("Unrelated page content must skip playback artwork updates", 0, counts.compositions)
            assertEquals("Unchanged player motion must skip playback artwork updates", 0, motionCounts.compositions)
        }
    }

    @Test
    fun sameLibraryPageUpdatesDoNotRecomposeUnchangedArtworkAndMotionReaders() {
        var state by mutableStateOf(
            LibraryUiState(
                entries = emptyList(), albumSort = AlbumSort.SERVER_DEFAULT,
                path = listOf(BrowseLocation("albums", "Albums")),
                browsePageKey = BrowsePageKey("offline-server", "albums"),
                browseViewState = BrowseViewState(), preferences = AppPreferences(),
                isSearching = false, servers = emptyList(), selectedServerId = "offline-server",
                browseLoadStatus = BrowseLoadStatus.LOADED, albumArtwork = null,
                currentTrackId = null, playbackState = RemotePlaybackState.PLAYING,
            ),
        )
        val counts = ContentCounts()
        val motionCounts = ContentCounts()
        compose.setContent {
            LibraryPageTransition(state) { page ->
                Text(page.currentTrackId.orEmpty())
                CountedImage(counts)
                CountedLibraryMotion(motionCounts)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { counts.compositions = 0; motionCounts.compositions = 0 }
        repeat(5) { index ->
            compose.runOnIdle { state = state.copy(currentTrackId = "track-$index") }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertEquals("Unchanged page content must skip track updates", 0, counts.compositions)
            assertEquals("Unchanged library motion must skip track updates", 0, motionCounts.compositions)
        }
    }

    @Test
    fun completedAlbumPagesAreDisposedWhileNewerCoversKeepAnimating() {
        val directory = LibraryUiState(
            entries = emptyList(), albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("albums", "Albums")),
            browsePageKey = BrowsePageKey("offline-server", "albums"),
            browseViewState = BrowseViewState(), preferences = AppPreferences(),
            isSearching = false, servers = emptyList(), selectedServerId = "offline-server",
            browseLoadStatus = BrowseLoadStatus.LOADED, albumArtwork = null,
            currentTrackId = null, playbackState = RemotePlaybackState.STOPPED,
        )
        val albums = List(3) { index ->
            directory.copy(
                browsePageKey = BrowsePageKey("offline-server", "album-$index"),
                path = directory.path + BrowseLocation("album-$index", pageKind = LibraryPageKind.ALBUM),
            )
        }
        var current by mutableStateOf(directory)
        val retainedPages = mutableSetOf<BrowsePageKey>()
        val motions = mutableMapOf<BrowsePageKey, LibraryPageMotionScope>()
        compose.setContent {
            LibraryPageTransition(current) { page ->
                val motion = checkNotNull(LocalLibraryPageMotion.current)
                SideEffect { motions[page.pageKey] = motion }
                DisposableEffect(page.pageKey) {
                    retainedPages.add(page.pageKey)
                    onDispose {
                        retainedPages.remove(page.pageKey)
                        motions.remove(page.pageKey)
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    if (page.pageKind == LibraryPageKind.ALBUM) {
                        Box(Modifier.align(Alignment.TopCenter)
                            .sharedAlbumArtwork(page.pageKey, AlbumArtworkCornerRadius)
                            .size(300.dp).background(Color.Cyan))
                    } else {
                        albums.forEachIndexed { index, album ->
                            Box(Modifier.align(Alignment.BottomStart).offset(x = (index * 80).dp, y = (-32).dp)
                                .sharedAlbumArtwork(album.pageKey, AlbumArtworkCornerRadius)
                                .size(64.dp).background(Color.Cyan))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun show(page: LibraryUiState, millis: Long) {
            compose.runOnIdle { current = page }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(millis)
        }
        show(albums[0], 96)
        show(directory, 32)
        show(albums[1], 96)
        val returning = compose.runOnIdle {
            val firstCover = checkNotNull(motions[directory.pageKey]?.covers?.get(
                AlbumArtworkTransitionKey(directory.pageKey, albums[0].pageKey),
            ))
            assertFalse("The previous return must run concurrently with the new opening", firstCover.isIdle)
            assertTrue(motions[albums[1].pageKey]!!.covers.values.any { !it.isIdle })
            firstCover
        }
        show(directory, 32)
        show(albums[2], 96)
        compose.mainClock.advanceTimeUntil(2_000) { returning.isIdle }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertFalse("Finished pages must not wait for unrelated cover flights", albums[0].pageKey in retainedPages)
            assertTrue("The newest cover should still be moving", motions[albums[2].pageKey]!!.covers.values.any { !it.isIdle })
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals("Only the selected page may remain after settling", setOf(albums[2].pageKey), retainedPages)
            assertTrue(motions[albums[2].pageKey]!!.isSettled)
        }
    }

    @Test
    fun flyingAndChangingCornersDoNotRecomposeOrRemeasureImageContent() {
        var expanded by mutableStateOf(false)
        val counts = ContentCounts()
        lateinit var handoff: CoverPressHandoff
        compose.setContent {
            val scope = rememberCoroutineScope()
            handoff = remember { CoverPressHandoff(scope) }
            SharedTransitionLayout(Modifier.fillMaxSize().testTag("motion-root")) {
                val sharedScope = this
                val transition = updateTransition(expanded, label = "flight-probe")
                val radius = transition.animateDp(
                    transitionSpec = { tween(COVER_OPEN_DURATION_MILLIS) }, label = "corner-probe",
                ) { if (it) 28.dp else 16.dp }
                transition.AnimatedContent(
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
                ) { large ->
                    Box(Modifier.fillMaxSize(), contentAlignment = if (large) Alignment.TopCenter else Alignment.BottomStart) {
                        Box(Modifier.sharedCoverArtwork(
                            sharedScope, this@AnimatedContent, "cover", COVER_OPEN_DURATION_MILLIS,
                            cornerRadius = { radius.value },
                            handoff = handoff,
                        ).size(if (large) 300.dp else 48.dp)) {
                            CountedImage(counts)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { handoff.capture("cover", .96f); expanded = true }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("motion-root").captureToImage()
        compose.runOnIdle { counts.compositions = 0; counts.measures = 0 }
        repeat(20) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("motion-root").captureToImage()
        compose.runOnIdle {
            // Allow a deferred initial layout, but not one measurement/composition per frame.
            assertTrue("Image recomposed ${counts.compositions} times in 20 flight frames", counts.compositions <= 1)
            assertTrue("Image remeasured ${counts.measures} times in 20 flight frames", counts.measures <= 2)
        }
    }

    private class ContentCounts(var compositions: Int = 0, var measures: Int = 0)

    @Composable
    private fun CountedPlayerMotion(counts: ContentCounts) {
        val motion = checkNotNull(LocalPlayerPageMotion.current)
        SideEffect { counts.compositions++ }
        Text(if (motion.isCurrentPage) "Current player page" else "Outgoing player page")
    }

    @Composable
    private fun CountedLibraryMotion(counts: ContentCounts) {
        val motion = checkNotNull(LocalLibraryPageMotion.current)
        SideEffect { counts.compositions++ }
        Text(if (motion.isCurrentPage) "Current library page" else "Outgoing library page")
    }

    @Composable
    private fun CountedImage(counts: ContentCounts) {
        SideEffect { counts.compositions++ }
        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
            counts.measures++
            val child = measurable.measure(constraints)
            layout(child.width, child.height) { child.place(0, 0) }
        }.background(Color.Cyan))
    }
}
