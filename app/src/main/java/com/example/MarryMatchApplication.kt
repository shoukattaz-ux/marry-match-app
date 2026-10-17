package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.imageLoader
import com.example.core.PhotoCache
import com.example.data.MarryMatchRepository
import com.example.push.PushNotifications

/**
 * Custom Application class that retains the [MarryMatchRepository]
 * instance across configuration changes and Activity recreation (such as screen rotations).
 */
class MarryMatchApplication : Application(), ImageLoaderFactory {
    val repository: MarryMatchRepository by lazy {
        MarryMatchRepository()
    }

    override fun onCreate() {
        super.onCreate()
        PushNotifications.createChannel(this)
        repository.onSignedOut = ::clearAccountCaches
    }

    /** After Log Out: no photos of the old account stay cached, and its chat transfers stop. */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun clearAccountCaches() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        val work = androidx.work.WorkManager.getInstance(this)
        work.cancelAllWorkByTag(com.example.data.media.MediaWork.TAG_SEND)
        work.cancelAllWorkByTag(com.example.data.media.MediaWork.TAG_DOWNLOAD)
        com.example.data.media.MediaFiles.outboxRoot(this).deleteRecursively()
    }

    /**
     * Photos are cached by file, not by their hourly signed link, and kept even though the
     * storage server asks for short caching: a photo file never changes after upload.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .components { add(PhotoCache.interceptor) }
        .build()
}
