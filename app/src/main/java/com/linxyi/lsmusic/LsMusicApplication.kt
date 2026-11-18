package com.linxyi.lsmusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import com.linxyi.lsmusic.backup.AppBackupStorage

class LsMusicApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppBackupStorage.recoverInterruptedImport(this)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(
                // A phone can reach its hotspot clients without a default internet network.
                connectivityChecker = { ConnectivityChecker.ONLINE },
                cacheStrategy = { ArtworkCacheStrategy },
            ))
        }
        .build()
}

@OptIn(ExperimentalCoilApi::class)
internal object ArtworkCacheStrategy : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult =
        if (cacheResponse.code in 200..299) CacheStrategy.ReadResult(cacheResponse)
        else CacheStrategy.ReadResult(networkRequest)

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult =
        if (networkResponse.code in 200..299) CacheStrategy.WriteResult(networkResponse)
        else CacheStrategy.WriteResult.DISABLED
}
