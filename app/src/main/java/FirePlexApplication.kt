package com.fireflicker.fireplex2

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class FirePlexApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("fireplex_artwork"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
