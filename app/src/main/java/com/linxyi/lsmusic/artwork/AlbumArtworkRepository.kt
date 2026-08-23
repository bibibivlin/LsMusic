package com.linxyi.lsmusic.artwork

import android.content.Context
import android.graphics.BitmapFactory
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.linxyi.lsmusic.dlna.ArtworkCandidate
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.rankArtworkCandidates
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class ArtworkDimensions(val width: Int, val height: Int) {
    val shortEdge: Int get() = minOf(width, height)
}

internal enum class AlbumArtworkSource { SERVER, EMBEDDED }

internal data class ResolvedAlbumArtwork(
    val uri: String,
    val dimensions: ArtworkDimensions,
    val source: AlbumArtworkSource,
)

internal fun interface ServerArtworkLoader {
    suspend fun load(uri: String, targetSizePx: Int): ArtworkDimensions?
}

internal fun interface EmbeddedArtworkSource {
    suspend fun read(track: MediaEntry): EmbeddedArtwork?
}

internal fun interface ArtworkBoundsReader {
    fun read(data: Any): ArtworkDimensions?
}

internal class AlbumArtworkRepository(
    private val serverArtworkLoader: ServerArtworkLoader,
    private val embeddedArtworkSource: EmbeddedArtworkSource,
    private val embeddedArtworkCache: EmbeddedArtworkDiskCache,
    private val boundsReader: ArtworkBoundsReader,
) {
    suspend fun resolve(
        serverId: String,
        artworkCandidates: List<ArtworkCandidate>,
        tracks: List<MediaEntry>,
        targetSizePx: Int,
    ): ResolvedAlbumArtwork? {
        val target = targetSizePx.coerceAtLeast(1)
        var best: ResolvedAlbumArtwork? = null

        rankArtworkCandidates(artworkCandidates).asSequence()
            .take(MAX_SERVER_CANDIDATES)
            .forEach { candidate ->
                val dimensions = serverArtworkLoader.load(candidate.uri, target) ?: return@forEach
                if (dimensions.width <= 0 || dimensions.height <= 0) return@forEach
                val resolved = ResolvedAlbumArtwork(candidate.uri, dimensions, AlbumArtworkSource.SERVER)
                if (dimensions.shortEdge >= target) return resolved
                val currentBest = best
                if (currentBest == null || dimensions.shortEdge > currentBest.dimensions.shortEdge) best = resolved
            }

        tracks.asSequence()
            .filter { !it.resourceUri.isNullOrBlank() }
            .distinctBy { it.resourceUri }
            .take(MAX_TRACKS_TO_SCAN)
            .forEach { track ->
                val identity = embeddedCacheIdentity(serverId, track)
                val cachedFile = embeddedArtworkCache.get(identity)
                if (cachedFile != null) {
                    val dimensions = boundsReader.read(cachedFile)
                    if (dimensions == null) {
                        embeddedArtworkCache.remove(identity)
                    } else {
                        val resolved = ResolvedAlbumArtwork(
                            uri = cachedFile.toURI().toString(),
                            dimensions = dimensions,
                            source = AlbumArtworkSource.EMBEDDED,
                        )
                        if (dimensions.shortEdge >= target) return resolved
                        val currentBest = best
                        if (currentBest == null || dimensions.shortEdge > currentBest.dimensions.shortEdge) {
                            best = resolved
                        }
                        return@forEach
                    }
                }

                val artwork = embeddedArtworkSource.read(track) ?: return@forEach
                val dimensions = boundsReader.read(artwork.bytes) ?: return@forEach
                val cached = embeddedArtworkCache.put(identity, artwork.bytes) ?: return@forEach
                val resolved = ResolvedAlbumArtwork(
                    uri = cached.toURI().toString(),
                    dimensions = dimensions,
                    source = AlbumArtworkSource.EMBEDDED,
                )
                if (dimensions.shortEdge >= target) return resolved
                val currentBest = best
                if (currentBest == null || dimensions.shortEdge > currentBest.dimensions.shortEdge) best = resolved
            }

        return best
    }

    private fun embeddedCacheIdentity(serverId: String, track: MediaEntry): String = buildString {
        append(EMBEDDED_ARTWORK_PARSER_VERSION)
        append('|')
        append(serverId)
        append('|')
        append(track.resourceUri)
        append('|')
        append(track.resourceSize ?: -1L)
    }

    companion object {
        private const val MAX_TRACKS_TO_SCAN = 3
        private const val MAX_SERVER_CANDIDATES = 8
        private const val EMBEDDED_ARTWORK_PARSER_VERSION = 1

        fun create(context: Context): AlbumArtworkRepository {
            val appContext = context.applicationContext
            val imageLoader = SingletonImageLoader.get(appContext)
            return AlbumArtworkRepository(
                serverArtworkLoader = ServerArtworkLoader { uri, targetSizePx ->
                    val request = ImageRequest.Builder(appContext)
                        .data(uri)
                        .size(targetSizePx)
                        .scale(Scale.FILL)
                        .precision(Precision.INEXACT)
                        .build()
                    (imageLoader.execute(request) as? SuccessResult)?.image?.let { image ->
                        ArtworkDimensions(image.width, image.height)
                    }
                },
                embeddedArtworkSource = EmbeddedArtworkSource(EmbeddedArtworkReader()::read),
                embeddedArtworkCache = EmbeddedArtworkDiskCache(
                    File(appContext.cacheDir, "embedded-album-art-v1"),
                ),
                boundsReader = ArtworkBoundsReader(::readArtworkBounds),
            )
        }
    }
}

internal class EmbeddedArtworkDiskCache(
    private val directory: File,
    private val maxBytes: Long = MAX_CACHE_BYTES,
    private val maxAgeMs: Long = MAX_CACHE_AGE_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun get(identity: String): File? {
        val file = fileFor(identity)
        if (!file.isFile || file.length() <= 0L) return null
        if (now() - file.lastModified() > maxAgeMs) {
            file.delete()
            return null
        }
        file.setLastModified(now())
        return file
    }

    @Synchronized
    fun put(identity: String, bytes: ByteArray): File? {
        if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return null
        if (!directory.exists() && !directory.mkdirs()) return null
        val destination = fileFor(identity)
        val temporary = File(directory, "${destination.name}.tmp")
        return runCatching {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (destination.exists() && !destination.delete()) error("Unable to replace artwork cache entry")
            if (!temporary.renameTo(destination)) error("Unable to commit artwork cache entry")
            destination.setLastModified(now())
            trim()
            destination.takeIf(File::isFile)
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    @Synchronized
    fun remove(identity: String) {
        fileFor(identity).delete()
    }

    @Synchronized
    internal fun trim() {
        if (!directory.isDirectory) return
        val cutoff = now() - maxAgeMs
        directory.listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".tmp") || it.lastModified() < cutoff) }
            .forEach(File::delete)
        val entries = directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".img") }
            .sortedBy(File::lastModified)
        var total = entries.sumOf(File::length)
        entries.forEach { file ->
            if (total <= maxBytes) return
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun fileFor(identity: String): File = File(directory, "${sha256(identity)}.img")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
        val MAX_CACHE_AGE_MS: Long = TimeUnit.DAYS.toMillis(30)
    }
}

private fun readArtworkBounds(data: Any): ArtworkDimensions? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when (data) {
        is ByteArray -> BitmapFactory.decodeByteArray(data, 0, data.size, options)
        is File -> BitmapFactory.decodeFile(data.absolutePath, options)
        else -> return null
    }
    return if (options.outWidth > 0 && options.outHeight > 0) {
        ArtworkDimensions(options.outWidth, options.outHeight)
    } else {
        null
    }
}
