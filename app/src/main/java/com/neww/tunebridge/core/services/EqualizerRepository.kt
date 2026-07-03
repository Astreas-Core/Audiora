package com.neww.tunebridge.core.services

import android.media.audiofx.Equalizer

class EqualizerRepository {
    private var equalizer: Equalizer? = null
    var isSupported: Boolean = true
    var isInitialized: Boolean = false
    
    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            if (equalizer != null) {
                equalizer?.release()
            }
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true
            isInitialized = true
            isSupported = true
        } catch (e: Exception) {
            e.printStackTrace()
            equalizer = null
            isSupported = false
        }
    }
    
    fun getBands(): List<Short> {
        val eq = equalizer ?: return emptyList()
        val bands = mutableListOf<Short>()
        for (i in 0 until eq.numberOfBands) {
            bands.add(i.toShort())
        }
        return bands
    }
    
    fun getBandLevelRange(): ShortArray? {
        return equalizer?.bandLevelRange
    }
    
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }
    
    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: 0
    }
    
    fun getCenterFreq(band: Short): Int {
        return equalizer?.getCenterFreq(band) ?: 0
    }
    
    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
