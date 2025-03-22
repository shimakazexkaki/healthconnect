package com.example.healthconnect.codelab.presentation.screen.heartrate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.HeartRateRecord
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.healthconnect.codelab.data.HealthConnectManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HeartRateScreen(
    healthConnectManager: HealthConnectManager
) {
    val viewModel: HeartRateViewModel = viewModel(
        factory = HeartRateViewModelFactory(healthConnectManager)
    )

    val heartRateData by viewModel.heartRateData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHeartRateData()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("過去30天的心跳速率") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (uiState) {
                HeartRateViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                HeartRateViewModel.UiState.Success -> {
                    if (heartRateData.heartRateRecords.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("沒有找到心跳速率數據")
                        }
                    } else {
                        LazyColumn {
                            items(heartRateData.heartRateRecords) { record ->
                                HeartRateItem(record)
                            }
                        }
                    }
                }
                is HeartRateViewModel.UiState.Error -> {
                    val errorState = uiState as HeartRateViewModel.UiState.Error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "錯誤: ${errorState.message}",
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeartRateItem(record: HeartRateRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())

            val timeStr = formatter.format(record.startTime)

            Text("時間: $timeStr")

            for (sample in record.samples) {
                Text("心跳速率: ${sample.beatsPerMinute} BPM")
            }
        }
    }
}
