package com.example.healthconnect.codelab.presentation.screen.heartrate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnect.codelab.data.HeartRateData
import com.example.healthconnect.codelab.data.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.health.connect.client.records.HeartRateRecord

class HeartRateViewModel(
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _heartRateData = MutableStateFlow<HeartRateData?>(null)
    val heartRateData: StateFlow<HeartRateData?> = _heartRateData

    private val _selectedStartTime = MutableStateFlow<Instant?>(null)
    val selectedStartTime: StateFlow<Instant?> = _selectedStartTime

    private val _selectedEndTime = MutableStateFlow<Instant?>(null)
    val selectedEndTime: StateFlow<Instant?> = _selectedEndTime

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    fun updateStartTime(time: Instant) {
        _selectedStartTime.value = time
    }

    fun updateEndTime(time: Instant) {
        _selectedEndTime.value = time
    }

    fun loadHeartRateData() {
        val start = _selectedStartTime.value
        val end = _selectedEndTime.value

        if (start != null && end != null) {
            viewModelScope.launch {
                try {
                    _uiState.value = UiState.Loading
                    val data = healthConnectManager.readHeartRateData(start, end)
                    _heartRateData.value = data
                    _uiState.value = UiState.Success
                } catch (e: Exception) {
                    _uiState.value = UiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}

class HeartRateViewModelFactory(
    private val healthConnectManager: HealthConnectManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HeartRateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HeartRateViewModel(healthConnectManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}