package com.neww.tunebridge.core.di

import com.google.gson.Gson
import com.neww.tunebridge.core.services.SpotifyScraper
import com.neww.tunebridge.core.db.LocalLibraryRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    single { LocalLibraryRepository(androidContext(), get()) }
    single { com.neww.tunebridge.core.db.SettingsRepository(androidContext()) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    single { Gson() }

    single { SpotifyScraper(get(), get()) }

    single { com.neww.tunebridge.core.services.YouTubeScraper(get(), get(), get()) }

    single { com.neww.tunebridge.core.services.LyricsRepository(androidContext(), get(), get()) }

    single { com.neww.tunebridge.core.services.DownloadRepository(androidContext(), get(), get(), get()) }
    
    single { com.neww.tunebridge.core.services.UpdateRepository(get()) }
    
    single { com.neww.tunebridge.core.services.EqualizerRepository() }

    single { com.neww.tunebridge.core.player.PlayerController(androidContext(), get(), get(), get(), get()) }
}
