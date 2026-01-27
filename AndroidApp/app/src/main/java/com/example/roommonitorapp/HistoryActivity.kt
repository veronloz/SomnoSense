package com.example.roommonitorapp

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class HistoryActivity : AppCompatActivity() {

    companion object {
        const val TAG = "HistoryActivity"
    }

    // MISMA base de datos que FirebaseManager
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

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            readings
        )
        listView.adapter = adapter

        titleText.text = "📊 Cargando histórico..."
    }

    private fun loadHistoricalData() {
        database
            .limitToLast(50)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    readings.clear()

                    if (!snapshot.hasChildren()) {
                        readings.add("📭 No hay datos disponibles aún")
                        readings.add("")
                        readings.add("💡 Deja la app abierta unos minutos")
                        adapter.notifyDataSetChanged()
                        titleText.text = "📊 Histórico"
                        return
                    }

                    var count = 0

                    snapshot.children
                        .toList()
                        .reversed() // más reciente primero
                        .forEach { data ->

                            try {
                                val timestamp =
                                    data.child("timestamp").getValue(Long::class.java) ?: 0L

                                val co =
                                    data.child("co").getValue(Double::class.java) ?: 0.0
                                val no2 =
                                    data.child("no2").getValue(Double::class.java) ?: 0.0
                                val nh3 =
                                    data.child("nh3").getValue(Double::class.java) ?: 0.0
                                val ch4 =
                                    data.child("ch4").getValue(Double::class.java) ?: 0.0
                                val etoh =
                                    data.child("c2h5oh").getValue(Double::class.java) ?: 0.0

                                val formattedDate =
                                    java.text.SimpleDateFormat(
                                        "dd/MM/yyyy HH:mm:ss",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date(timestamp))

                                val formattedReading = """
                                    📅 $formattedDate

                                    CO: ${"%.2f".format(co)} ppm
                                    NO₂: ${"%.2f".format(no2)} ppm
                                    NH₃: ${"%.2f".format(nh3)} ppm
                                    CH₄: ${"%.2f".format(ch4)} ppm
                                    C₂H₅OH: ${"%.2f".format(etoh)} ppm
                                    ────────────────────
                                                                    """.trimIndent()

                                readings.add(formattedReading)
                                count++

                            } catch (e: Exception) {
                                Log.e(TAG, "Error parseando registro", e)
                            }
                        }

                    adapter.notifyDataSetChanged()
                    titleText.text = "📊 Histórico ($count registros)"

                    Log.d(TAG, "Cargados $count registros")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error Firebase: ${error.message}")
                    readings.clear()
                    readings.add("❌ Error al cargar el histórico")
                    readings.add(error.message)
                    adapter.notifyDataSetChanged()
                    titleText.text = "📊 Error"
                }
            })
    }
}
