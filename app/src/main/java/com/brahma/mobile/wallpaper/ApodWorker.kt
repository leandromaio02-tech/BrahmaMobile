package com.brahma.mobile.wallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ApodWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ApodManager(applicationContext).refreshWallpaper()
        return Result.success()
    }
}
