// 位置: presentation/screen/heartrate/HeartRateViewModel.kt
package com.example.healthconnect.codelab.presentation.screen.heartrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.healthconnect.codelab.data.HeartRateData
import com.example.healthconnect.codelab.data.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class HeartRateViewModel(
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _heartRateData = MutableStateFlow(HeartRateData(emptyList(), Instant.EPOCH, Instant.EPOCH))
    val heartRateData: StateFlow<HeartRateData> = _heartRateData

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    fun loadHeartRateData() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                // 計算過去30天的時間範圍
                val endTime = Instant.now()
                val startTime = endTime.minus(30, ChronoUnit.DAYS)

                val heartRateData = healthConnectManager.readHeartRateData(
                    startTime = startTime,
                    endTime = endTime
                )

                _heartRateData.value = heartRateData
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
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