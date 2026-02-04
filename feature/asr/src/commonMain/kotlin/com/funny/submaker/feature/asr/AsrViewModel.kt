package com.funny.submaker.feature.asr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AsrViewModel : ViewModel() {
    var apiBaseUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    var running by mutableStateOf(false)
    var lastResult by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun clearError() {
        errorMessage = null
    }
}

