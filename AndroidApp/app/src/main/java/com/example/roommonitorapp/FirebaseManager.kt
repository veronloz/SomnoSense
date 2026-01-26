package com.example.roommonitorapp

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class FirebaseManager {

    companion object {
        const val TAG = "FirebaseManager"
    }

    // Firebase se inicializa automáticamente, no necesitas FirebaseApp.initializeApp()
    private val firebaseDB = FirebaseDatabase
        .getInstance("https://somnosense-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("somnosense/data")

    private var mockTimer: Timer? = null

    // Ya no necesitas el método initialize() - Firebase se inicializa solo

    fun sendSensorData(temperature: Float, humidity: Float, gasLevel: Int, deviceId: String = "REAL_DEVICE") {
        val data = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "temperature" to temperature,
            "humidity" to humidity,
            "gasLevel" to gasLevel,
            "deviceId" to deviceId
        )

        firebaseDB.push().setValue(data)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Data sent to Firebase successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to send data: ${e.message}")
            }
    }

    // Mock data para pruebas
    fun startMockData(callback: (Float, Float, Int) -> Unit) {
        mockTimer?.cancel()
        mockTimer = Timer()

        mockTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val temperature = (18..25).random() + Random().nextFloat()
                val humidity = (40..60).random() + Random().nextFloat()
                val gasLevel = (50..400).random()

                callback(temperature, humidity, gasLevel)
                sendSensorData(temperature, humidity, gasLevel, "MOCK_DEVICE_001")
                Log.d(TAG, "🧪 Mock data generated & sent")
            }
        }, 0, 5000) // Cada 5 segundos
    }

    fun stopMockData() {
        mockTimer?.cancel()
        mockTimer = null
        Log.d(TAG, "Mock data stopped")
    }

    fun cleanup() {
        stopMockData()
    }
}