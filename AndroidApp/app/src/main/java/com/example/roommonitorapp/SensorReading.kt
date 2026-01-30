package com.example.roommonitorapp

data class SensorReading(
    val timestamp: Long = 0,
    val temperature: Float = 0.0f,
    val humidity: Float = 0.0f,
    val gasLevel: Int = 0
) {
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}