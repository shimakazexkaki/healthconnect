package com.example.healthconnect.codelab.data

import androidx.health.connect.client.records.HeartRateRecord
import java.time.Instant

data class HeartRateData(
    val heartRateRecords: List<HeartRateRecord>,
    val startTime: Instant,
    val endTime: Instant
)