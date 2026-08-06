package com.techilyfly.tfplans

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.techilyfly.tfplans.di.AppContainer
import com.techilyfly.tfplans.di.DefaultAppContainer
import com.google.android.gms.ads.MobileAds
import com.techilyfly.tfplans.data.SyncWorker
import java.util.concurrent.TimeUnit

class TFPlansApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        container = DefaultAppContainer(this)
        
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TFPlansBackgroundSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
