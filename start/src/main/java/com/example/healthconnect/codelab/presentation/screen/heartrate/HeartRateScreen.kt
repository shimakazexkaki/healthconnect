package com.example.healthconnect.codelab.presentation.screen.heartrate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.healthconnect.codelab.data.HealthConnectManager
import com.example.healthconnect.codelab.data.HeartRateData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar

@Composable
fun HeartRateScreen(
    healthConnectManager: HealthConnectManager,
    viewModel: HeartRateViewModel = viewModel(
        factory = HeartRateViewModelFactory(healthConnectManager)
    )
) {
    val startTime by viewModel.selectedStartTime.collectAsState()
    val endTime by viewModel.selectedEndTime.collectAsState()
    val heartRateData by viewModel.heartRateData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // 預設查詢區間 (過去 1 天 ~ 現在)
    LaunchedEffect(Unit) {
        viewModel.updateStartTime(Instant.now().minus(1, ChronoUnit.DAYS))
        viewModel.updateEndTime(Instant.now())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1) 時間選擇器
        DateTimePicker(
            label = "開始時間",
            currentTime = startTime,
            onTimeSelected = { newInstant -> viewModel.updateStartTime(newInstant) }
        )
        DateTimePicker(
            label = "結束時間",
            currentTime = endTime,
            onTimeSelected = { newInstant -> viewModel.updateEndTime(newInstant) }
        )

        // 2) 載入資料按鈕
        Button(
            onClick = { viewModel.loadHeartRateData() },
            enabled = (startTime != null && endTime != null),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("載入心跳速率")
        }

        // 3) 根據 UI 狀態顯示
        when (uiState) {
            is HeartRateViewModel.UiState.Loading -> {
                Text("載入中...")
            }
            is HeartRateViewModel.UiState.Success -> {
                heartRateData?.let { data ->
                    Text("找到 ${data.heartRateRecords.size} 筆心跳速率記錄")

                    // 3.1) 顯示折線圖
                    HeartRateLineChart(data)

                    // 3.2) 在折線圖下方顯示每筆樣本 (raw data) 列表
                    HeartRateDataList(data)
                }
            }
            is HeartRateViewModel.UiState.Error -> {
                val msg = (uiState as HeartRateViewModel.UiState.Error).message
                Text("載入錯誤：$msg")
            }
        }
    }
}

/**
 * 日期 & 時間挑選器：使用 DatePickerDialog 與 TimePickerDialog 分別選擇日期與時間。
 */
@Composable
fun DateTimePicker(
    label: String,
    currentTime: Instant?,
    onTimeSelected: (Instant) -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId)
    val displayText = currentTime?.let { formatter.format(it) } ?: "尚未選擇"

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "$label：$displayText")
        Row {
            Button(onClick = { showDatePicker = true }, modifier = Modifier.padding(end = 8.dp)) {
                Text("選擇日期")
            }
            Button(onClick = { showTimePicker = true }) {
                Text("選擇時間")
            }
        }
    }

    // 彈出 DatePickerDialog
    if (showDatePicker) {
        val cal = Calendar.getInstance().apply {
            if (currentTime != null) timeInMillis = currentTime.toEpochMilli()
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                onTimeSelected(newCal.toInstant())
                showDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // 彈出 TimePickerDialog
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            if (currentTime != null) timeInMillis = currentTime.toEpochMilli()
        }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onTimeSelected(newCal.toInstant())
                showTimePicker = false
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }
}

/**
 * 顯示折線圖（含輔助格線、X 軸日期標籤、Y 軸 BPM 標籤）。
 */
@Composable
fun HeartRateLineChart(heartRateData: HeartRateData) {
    val records = heartRateData.heartRateRecords
    if (records.isEmpty()) {
        Text("沒有心跳資料")
        return
    }

    val dataPoints = records.flatMap { record ->
        record.samples.map { sample ->
            Pair(record.startTime.toEpochMilli(), sample.beatsPerMinute)
        }
    }.sortedBy { it.first }

    if (dataPoints.isEmpty()) {
        Text("樣本資料為空")
        return
    }

    val minBpm = dataPoints.minOf { it.second }
    val maxBpm = dataPoints.maxOf { it.second }
    val startMillis = heartRateData.startTime.toEpochMilli()
    val endMillis = heartRateData.endTime.toEpochMilli()

    // 刻度數
    val xTickCount = 5
    val yTickCount = 5

    val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(250.dp)
        .padding(8.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val paddingLeft = 40f
        val paddingBottom = 30f

        val chartWidth = canvasWidth - paddingLeft
        val chartHeight = canvasHeight - paddingBottom

        // X 軸 & Y 軸
        drawLine(
            color = Color.Black,
            start = Offset(paddingLeft, chartHeight),
            end = Offset(canvasWidth, chartHeight),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Black,
            start = Offset(paddingLeft, 0f),
            end = Offset(paddingLeft, chartHeight),
            strokeWidth = 2f
        )

        // 格線
        for (i in 0..xTickCount) {
            val x = paddingLeft + i * (chartWidth / xTickCount)
            drawLine(
                color = Color.LightGray,
                start = Offset(x, 0f),
                end = Offset(x, chartHeight),
                strokeWidth = 1f
            )
        }
        for (i in 0..yTickCount) {
            val y = i * (chartHeight / yTickCount)
            drawLine(
                color = Color.LightGray,
                start = Offset(paddingLeft, chartHeight - y),
                end = Offset(canvasWidth, chartHeight - y),
                strokeWidth = 1f
            )
        }

        // 資料點映射
        val points = dataPoints.map { (timeMillis, bpm) ->
            val xRatio = (timeMillis - startMillis).toFloat() / (endMillis - startMillis).toFloat()
            val x = paddingLeft + xRatio * chartWidth

            val bpmRatio = (bpm - minBpm).toFloat() / (maxBpm - minBpm).toFloat()
            val y = chartHeight - (bpmRatio * chartHeight)

            Offset(x, y)
        }

        // 畫折線
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color.Red,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }

        // X 軸刻度與標籤
        for (i in 0..xTickCount) {
            val tickX = paddingLeft + i * (chartWidth / xTickCount)
            drawLine(
                color = Color.Black,
                start = Offset(tickX, chartHeight),
                end = Offset(tickX, chartHeight + 5f),
                strokeWidth = 2f
            )
            val tickTimeMillis = startMillis + i * ((endMillis - startMillis) / xTickCount)
            val label = timeFormatter.format(Instant.ofEpochMilli(tickTimeMillis))
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    label,
                    tickX - 30f,
                    chartHeight + 25f,
                    Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 28f
                    }
                )
            }
        }

        // Y 軸刻度與標籤
        for (i in 0..yTickCount) {
            val tickY = i * (chartHeight / yTickCount)
            drawLine(
                color = Color.Black,
                start = Offset(paddingLeft - 5f, chartHeight - tickY),
                end = Offset(paddingLeft, chartHeight - tickY),
                strokeWidth = 2f
            )
            val bpmValue = minBpm + i * ((maxBpm - minBpm) / yTickCount)
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    bpmValue.toString(),
                    5f,
                    chartHeight - tickY + 10f,
                    Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 28f
                    }
                )
            }
        }
    }
}

/**
 * 在折線圖下方，顯示原始的心跳樣本清單 (時間 + 心跳速率)。
 */
@Composable
fun HeartRateDataList(heartRateData: HeartRateData) {
    // 如果您想跟折線圖用同樣的時間排序邏輯，可重複使用相同的 flatten 步驟
    val zoneId = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId)

    // 取出每筆 record 裡的 samples，並組合成一個 List
    val dataPoints = heartRateData.heartRateRecords.flatMap { record ->
        record.samples.map { sample ->
            // 這裡假設使用 record.startTime 作為該筆樣本時間
            // 若 sample 有自己的時間屬性，可改成 sample.time
            Pair(record.startTime, sample.beatsPerMinute)
        }
    }.sortedBy { it.first }

    // 若沒資料
    if (dataPoints.isEmpty()) {
        Text("沒有心跳樣本資料")
        return
    }

    // 使用 LazyColumn 顯示
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(dataPoints.size) { index ->
            val (timeInstant, bpm) = dataPoints[index]
            val timeStr = dateFormatter.format(timeInstant)
            Column(modifier = Modifier.padding(8.dp)) {
                Text("時間: $timeStr")
                Text("心跳速率: $bpm BPM")
            }
        }
    }
}
