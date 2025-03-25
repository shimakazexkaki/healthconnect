package com.example.healthconnect.codelab.presentation.screen.heartrate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
    viewModel: HeartRateViewModel = viewModel(factory = HeartRateViewModelFactory(healthConnectManager))
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 時間選擇器
        DateTimePicker(
            label = "開始時間",
            currentTime = startTime,
            onTimeSelected = { viewModel.updateStartTime(it) }
        )
        DateTimePicker(
            label = "結束時間",
            currentTime = endTime,
            onTimeSelected = { viewModel.updateEndTime(it) }
        )

        // 載入資料按鈕
        Button(
            onClick = { viewModel.loadHeartRateData() },
            enabled = (startTime != null && endTime != null),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text("載入心跳速率")
        }

        // 根據 UI 狀態顯示
        when (uiState) {
            is HeartRateViewModel.UiState.Loading -> Text("載入中...")
            is HeartRateViewModel.UiState.Success -> {
                heartRateData?.let { data ->
                    Text("找到 ${data.heartRateRecords.size} 筆心跳速率記錄")
                    // 顯示折線圖與浮動 tooltip
                    HeartRateLineChart(data)
                    // Raw Data 清單
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
        Text("$label：$displayText")
        Row {
            Button(onClick = { showDatePicker = true }, modifier = Modifier.padding(end = 8.dp)) {
                Text("選擇日期")
            }
            Button(onClick = { showTimePicker = true }) {
                Text("選擇時間")
            }
        }
    }

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
 * 顯示折線圖，並支援點擊/長按某個點後以 Popup 顯示 tooltip（時間/BPM）。
 */
@Composable
fun HeartRateLineChart(heartRateData: HeartRateData) {
    val records = heartRateData.heartRateRecords
    if (records.isEmpty()) {
        Text("沒有心跳資料")
        return
    }

    // 將每筆紀錄的 startTime 與 beatsPerMinute 統一存成 DataPoint（以 record.startTime 為時間參考）
    val rawPoints = records.flatMap { record ->
        record.samples.map { sample ->
            DataPoint(
                timeMillis = record.startTime.toEpochMilli(),
                bpm = sample.beatsPerMinute,
                offset = Offset.Zero
            )
        }
    }.sortedBy { it.timeMillis }

    if (rawPoints.isEmpty()) {
        Text("樣本資料為空")
        return
    }

    val minBpm = rawPoints.minOf { it.bpm }
    val maxBpm = rawPoints.maxOf { it.bpm }
    val startMillis = heartRateData.startTime.toEpochMilli()
    val endMillis = heartRateData.endTime.toEpochMilli()

    val xTickCount = 5
    val yTickCount = 5
    val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

    // 用來存放使用者點擊後選到的資料點
    var selectedPoint by remember { mutableStateOf<DataPoint?>(null) }
    // 用來存放計算後的資料點（包含畫布 offset）
    val computedPoints = remember { mutableStateListOf<DataPoint>() }

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (computedPoints.isNotEmpty()) {
                            val nearest = computedPoints.minByOrNull { dp ->
                                (dp.offset - tapOffset).getDistance()
                            }
                            selectedPoint = nearest
                        }
                    },
                    onLongPress = { longPressOffset ->
                        if (computedPoints.isNotEmpty()) {
                            val nearest = computedPoints.minByOrNull { dp ->
                                (dp.offset - longPressOffset).getDistance()
                            }
                            selectedPoint = nearest
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val paddingLeft = 40f
            val paddingBottom = 30f
            val chartWidth = canvasWidth - paddingLeft
            val chartHeight = canvasHeight - paddingBottom

            // 繪製 X 軸與 Y 軸
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

            // 繪製輔助格線
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

            // 計算每個資料點在畫布上的座標
            val updatedList = rawPoints.map { dp ->
                val xRatio = (dp.timeMillis - startMillis).toFloat() / (endMillis - startMillis).toFloat()
                val x = paddingLeft + xRatio * chartWidth

                val bpmRatio = (dp.bpm - minBpm).toFloat() / (maxBpm - minBpm).toFloat()
                val y = chartHeight - (bpmRatio * chartHeight)
                dp.copy(offset = Offset(x, y))
            }

            computedPoints.clear()
            computedPoints.addAll(updatedList)

            // 畫折線
            for (i in 0 until updatedList.size - 1) {
                drawLine(
                    color = Color.Red,
                    start = updatedList[i].offset,
                    end = updatedList[i + 1].offset,
                    strokeWidth = 3f
                )
            }

            // 繪製 X 軸刻度與標籤
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

            // 繪製 Y 軸刻度與標籤
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

        // 使用 Popup 顯示 tooltip，如果有選中的資料點
        selectedPoint?.let { point ->
            // 使用 LocalDensity 將選中點的座標轉換為 dp
            Popup(
                onDismissRequest = { selectedPoint = null },
                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize
                    ): androidx.compose.ui.unit.IntOffset {
                        return with(density) {
                            // 計算 Popup 的 x, y 座標，使其真正對齊畫布內的 point.offset
                            // anchorBounds.left + point.offset.x → 在整個螢幕中的 x 座標
                            // anchorBounds.top + point.offset.y → 在整個螢幕中的 y 座標
                            val popupX = (anchorBounds.left + point.offset.x - popupContentSize.width / 2).toInt()
                            // 讓 Popup 底部位於點之上，可再往上移一點（例如 8.dp）避免覆蓋資料點
                            val popupY = (anchorBounds.top + point.offset.y - popupContentSize.height - 8.dp.toPx()).toInt()

                            IntOffset(popupX, popupY)
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(8.dp)
                ) {
                    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                    val timeStr = dateFmt.format(Instant.ofEpochMilli(point.timeMillis))
                    Column {
                        Text("時間: $timeStr", color = Color.White, fontSize = 14.sp)
                        Text("心率: ${point.bpm} BPM", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * DataPoint：儲存一個資料點的資訊
 */
private data class DataPoint(
    val timeMillis: Long,
    val bpm: Long,
    val offset: Offset
)

/**
 * Raw Data 列表：顯示每筆 record 及其 samples 的時間 (record.startTime) 與心跳速率。
 */
@Composable
fun HeartRateDataList(heartRateData: HeartRateData) {
    val zoneId = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId)

    // 統一使用 record.startTime 作為時間
    val dataPoints = heartRateData.heartRateRecords.flatMap { record ->
        record.samples.map { sample ->
            Pair(record.startTime, sample.beatsPerMinute)
        }
    }.sortedBy { it.first }

    if (dataPoints.isEmpty()) {
        Text("沒有心跳樣本資料")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
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


