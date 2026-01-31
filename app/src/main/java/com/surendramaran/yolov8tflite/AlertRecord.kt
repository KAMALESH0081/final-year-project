package com.surendramaran.yolov8tflite

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AlertRecord(
    val className: String,
    val confidence: Float,
    val speed: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}