package com.neww.tunebridge.core.services

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EqualizerRepository {
    private var equalizer: Equalizer? = null
    
    private val _isSupported = MutableStateFlow(true)
    val isSupported: StateFlow<Boolean> = _isSupported.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    fun initEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            _lastError.value = "Invalid audio session ID: $audioSessionId"
            return
        }
        try {
            if (equalizer != null) {
                equalizer?.release()
            }
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true
            _isInitialized.value = true
            _isSupported.value = true
            _lastError.value = null
        } catch (e: Exception) {
            e.printStackTrace()
            equalizer = null
            _isSupported.value = false
            _lastError.value = e.message ?: e.toString()
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
