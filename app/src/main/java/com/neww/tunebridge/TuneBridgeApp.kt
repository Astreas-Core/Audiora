package com.neww.tunebridge

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TuneBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TuneBridgeApp)
            modules(com.neww.tunebridge.core.di.appModule)
        }
    }
}
