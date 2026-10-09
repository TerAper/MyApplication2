package com.teraper.printmaster

import android.app.Application
import com.teraper.printmaster.core.data.calls.CallRecordingsSync
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PrintMasterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CallRecordingsSync.schedule(this)
    }
}
