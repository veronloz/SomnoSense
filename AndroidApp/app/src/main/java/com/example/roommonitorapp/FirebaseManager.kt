package com.example.roommonitorapp

import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class FirebaseManager {

    companion object {
        const val TAG = "FirebaseManager"
    }

    // Base de datos explícita (mejor para labs)
    private val firebaseDB = FirebaseDatabase
        .getInstance("https://somnosense-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("somnosense/data")

    fun sendGasData(
        co: Float,
        no2: Float,
        nh3: Float,
        ch4: Float,
        etoh: Float
    ) {
        val data = mapOf(
            "co" to co,
            "no2" to no2,
            "nh3" to nh3,
            "ch4" to ch4,
            "c2h5oh" to etoh,
            "timestamp" to System.currentTimeMillis()
        )

        firebaseDB.push()
            .setValue(data)
            .addOnSuccessListener {
                Log.d(TAG, "Datos enviados correctamente a Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error enviando datos a Firebase", e)
            }
    }
    
    fun sendMockGasData() {
        val mockData = mapOf(
            "co" to (5..15).random() + Math.random(),
            "no2" to (2..8).random() + Math.random(),
            "nh3" to (10..25).random() + Math.random(),
            "ch4" to (5..12).random() + Math.random(),
            "c2h5oh" to (1..6).random() + Math.random(),
            "timestamp" to System.currentTimeMillis()
        )

        firebaseDB.push().setValue(mockData)
    }

}
