package com.example.roommonitorapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HistoryActivity : AppCompatActivity() {

    // ← USAR EL MISMO PATRÓN QUE FirebaseManager
    private val database = FirebaseDatabase
        .getInstance("https://somnosense-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("somnosense/data")

    private val readings = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var titleText: TextView
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initializeViews()
        loadHistoricalData()
    }

    private fun initializeViews() {
        titleText = findViewById(R.id.tvHistoryTitle)
        listView = findViewById(R.id.listViewHistory)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, readings)
        listView.adapter = adapter

        titleText.text = "📊 Cargando datos..."
    }

    private fun loadHistoricalData() {
        database
            .limitToLast(50) // Últimas 50 lecturas
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    readings.clear()

                    if (snapshot.childrenCount == 0L) {
                        readings.add("📭 No hay datos disponibles")
                        readings.add("")
                        readings.add("💡 Tip: Deja la app abierta unos minutos")
                        readings.add("para que se generen datos de prueba")
                        adapter.notifyDataSetChanged()
                        titleText.text = "📊 Histórico de Mediciones"
                        return
                    }

                    var count = 0
                    // Invertir orden para mostrar más reciente primero
                    snapshot.children.reversed().forEach { dataSnapshot ->
                        try {
                            // Leer datos como Map (mismo formato que FirebaseManager)
                            val timestamp = dataSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val temperature = dataSnapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                            val humidity = dataSnapshot.child("humidity").getValue(Double::class.java) ?: 0.0
                            val gasLevel = dataSnapshot.child("gasLevel").getValue(Int::class.java) ?: 0
                            val deviceId = dataSnapshot.child("deviceId").getValue(String::class.java) ?: "Unknown"

                            count++
                            val formattedDate = java.text.SimpleDateFormat(
                                "dd/MM/yyyy HH:mm:ss",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(timestamp))

                            val airQuality = getAirQuality(gasLevel)

                            val formattedReading = """
                                📅 $formattedDate
                                🌡️ Temperatura: ${String.format("%.1f", temperature)}°C
                                💧 Humedad: ${String.format("%.1f", humidity)}%
                                🌫️ Nivel de Gas: $gasLevel
                                $airQuality
                                📱 Dispositivo: $deviceId
                                ────────────────────
                            """.trimIndent()

                            readings.add(formattedReading)
                        } catch (e: Exception) {
                            Log.e("History", "Error al parsear lectura: ${e.message}")
                        }
                    }

                    adapter.notifyDataSetChanged()
                    titleText.text = "📊 Histórico ($count registros)"

                    Log.d("History", "✅ Cargados $count registros desde Firebase")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("History", "❌ Error Firebase: ${error.message}")
                    readings.clear()
                    readings.add("❌ Error al cargar datos")
                    readings.add("")
                    readings.add("Detalle: ${error.message}")
                    adapter.notifyDataSetChanged()
                    titleText.text = "📊 Error en Histórico"
                }
            })
    }

    private fun getAirQuality(gasLevel: Int): String {
        return when {
            gasLevel < 100 -> "✅ Calidad del aire: Excelente"
            gasLevel < 300 -> "⚠️ Calidad del aire: Buena"
            gasLevel < 500 -> "🔶 Calidad del aire: Regular"
            else -> "🔴 Calidad del aire: Mala"
        }
    }
}