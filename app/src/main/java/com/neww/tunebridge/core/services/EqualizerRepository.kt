package com.neww.tunebridge.core.services

import android.media.audiofx.Equalizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neww.tunebridge.core.db.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EqualizerRepository(
    private val settingsRepository: SettingsRepository,
    private val gson: Gson
) {
    private var equalizer: Equalizer? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _isSupported = MutableStateFlow(true)
    val isSupported: StateFlow<Boolean> = _isSupported.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    val eqUpdateTrigger = MutableStateFlow(0)
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()
    
    val currentPreset = settingsRepository.eqPresetFlow

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
            
            // Extract presets
            val presetList = mutableListOf<String>()
            val numPresets = equalizer?.numberOfPresets ?: 0
            for (i in 0 until numPresets) {
                equalizer?.getPresetName(i.toShort())?.let { presetList.add(it) }
            }
            if (!presetList.contains("Bass Boost")) {
                presetList.add("Bass Boost")
            }
            presetList.add("Custom")
            _presets.value = presetList

            // Restore state from Settings
            scope.launch {
                restoreSavedState()
            }
            
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
    
    private suspend fun restoreSavedState() {
        val eq = equalizer ?: return
        val savedPreset = settingsRepository.eqPresetFlow.first()
        val savedBandsJson = settingsRepository.eqBandsFlow.first()
        
        if (savedPreset == "Bass Boost") {
            applyBassBoost(eq)
        } else if (savedPreset != "Custom" && _presets.value.contains(savedPreset)) {
            val presetIndex = _presets.value.indexOf(savedPreset)
            if (presetIndex >= 0 && presetIndex < (eq.numberOfPresets ?: 0)) {
                eq.usePreset(presetIndex.toShort())
                eqUpdateTrigger.value++
            }
        } else if (savedBandsJson.isNotBlank() && savedBandsJson != "{}") {
            try {
                val type = object : TypeToken<Map<String, Short>>() {}.type
                val savedBands: Map<String, Short> = gson.fromJson(savedBandsJson, type)
                for ((bandStr, level) in savedBands) {
                    val band = bandStr.toShortOrNull() ?: continue
                    if (band < eq.numberOfBands) {
                        eq.setBandLevel(band, level)
                    }
                }
                eqUpdateTrigger.value++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        if (presetName == "Custom") {
            scope.launch { settingsRepository.setEqPreset("Custom") }
            return
        }
        if (presetName == "Bass Boost") {
            applyBassBoost(eq)
            scope.launch { settingsRepository.setEqPreset("Bass Boost") }
            return
        }
        val presetIndex = _presets.value.indexOf(presetName)
        if (presetIndex >= 0 && presetIndex < eq.numberOfPresets) {
            eq.usePreset(presetIndex.toShort())
            eqUpdateTrigger.value++
            scope.launch { settingsRepository.setEqPreset(presetName) }
        }
    }
    
    private fun applyBassBoost(eq: Equalizer) {
        for (i in 0 until eq.numberOfBands) {
            val band = i.toShort()
            val freq = eq.getCenterFreq(band)
            if (freq < 150000) { // < 150Hz
                eq.setBandLevel(band, 1500.toShort())
            } else if (freq < 300000) { // < 300Hz
                eq.setBandLevel(band, 700.toShort())
            } else {
                eq.setBandLevel(band, 0.toShort())
            }
        }
        eqUpdateTrigger.value++
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
        scope.launch {
            settingsRepository.setEqPreset("Custom")
            saveCustomBands()
        }
    }
    
    private suspend fun saveCustomBands() {
        val eq = equalizer ?: return
        val map = mutableMapOf<String, Short>()
        for (i in 0 until eq.numberOfBands) {
            val band = i.toShort()
            map[band.toString()] = eq.getBandLevel(band)
        }
        val json = gson.toJson(map)
        settingsRepository.setEqBands(json)
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
