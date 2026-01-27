package com.example.roommonitorapp

data class SensorReading(
    val timestamp: Long = 0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val gas: Int = 0,
    val airQuality: String = ""
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