package com.example.roommonitorapp

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LocalAnalysis : AppCompatActivity() {

    private lateinit var tvSleepScore: TextView
    private lateinit var tvAnalysisDetails: TextView
    private lateinit var lineChart: LineChart
    private lateinit var btnRefresh: Button

    private val database = FirebaseDatabase
        .getInstance("https://somnosense-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("somnosense/data")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        tvSleepScore = findViewById(R.id.tvSleepScore)
        tvAnalysisDetails = findViewById(R.id.tvAnalysisDetails)
        lineChart = findViewById(R.id.lineChart)
        btnRefresh = findViewById(R.id.btnRefreshAnalysis)

        btnRefresh.setOnClickListener {
            runLocalSCIAnalysis()
        }

        runLocalSCIAnalysis()
    }

    /**
     * 🔥 Reimplementación LOCAL del Cloud Function
     * calculate_sleep_score_on_demand
     */
    private fun runLocalSCIAnalysis() {
        btnRefresh.isEnabled = false
        tvAnalysisDetails.text = "Calculando SCI local..."

        database.limitToLast(1000).get()
            .addOnSuccessListener { snapshot ->

                val temps = mutableListOf<Double>()
                val hums = mutableListOf<Double>()
                val cos = mutableListOf<Double>()
                val sounds = mutableListOf<Double>()
                val sciIndexes = mutableListOf<Double>()

                val tempEntries = ArrayList<Entry>()
                var index = 0f

                for (child in snapshot.children) {

                    val env = child.child("environment")
                    val gas = child.child("gas")

                    val temp = env.child("temp").getValue(Double::class.java) ?: continue
                    val hum = env.child("hum").getValue(Double::class.java) ?: 0.0
                    val co = gas.child("co").getValue(Double::class.java) ?: 0.0
                    val sound = child.child("sound").getValue(Double::class.java) ?: 0.0

                    temps.add(temp)
                    hums.add(hum)
                    cos.add(co)
                    sounds.add(sound)

                    tempEntries.add(Entry(index, temp.toFloat()))
                    index++

                    // ===== SCI PER RECORD =====

                    // 1️⃣ TEMPERATURA (0..10)
                    val sTemp = when {
                        temp in 18.0..22.0 -> 10.0
                        temp < 18.0 -> max(0.0, 10 - (18 - temp) * 2)
                        else -> max(0.0, 10 - (temp - 22) * 1.5)
                    }

                    // 2️⃣ CO (0..10) — ideal ~6.5 ppm
                    val sCo = max(0.0, 10 - max(0.0, co - 6.5) * 0.01)

                    // 3️⃣ SONIDO (0..10)
                    val sSound = max(0.0, 10 - sound * 1.0)

                    // 4️⃣ HUMEDAD (0..10)
                    val sHum = if (hum in 40.0..60.0) {
                        10.0
                    } else {
                        val dist = min(abs(hum - 40), abs(hum - 60))
                        max(0.0, 10 - dist * 0.5)
                    }

                    // Weighted SCI (0..10)
                    val sci = (sTemp * 0.40) +
                            (sCo * 0.30) +
                            (sHum * 0.20) +
                            (sSound * 0.10)

                    sciIndexes.add(sci)
                }

                val count = temps.size
                if (count == 0) {
                    tvAnalysisDetails.text = "No hay datos suficientes."
                    btnRefresh.isEnabled = true
                    return@addOnSuccessListener
                }

                val avgTemp = temps.average()
                val avgHum = hums.average()
                val avgCo = cos.average()
                val avgSound = sounds.average()
                val meanSCI = sciIndexes.average()

                // Escala 0..10 → 0..100
                val score = min(100, max(0, (meanSCI * 10).toInt()))

                // UI
                tvSleepScore.text = "Puntaje: $score / 100"

                tvAnalysisDetails.text = """
                    Índice de Confort del Sueño (SCI):
                    
                    🌡 Temp media: ${"%.1f".format(avgTemp)} °C
                    💧 Hum media: ${"%.1f".format(avgHum)} %
                    💨 CO medio: ${"%.2f".format(avgCo)} ppm
                    🔊 Ruido medio: ${"%.1f".format(avgSound)}
                    
                    📊 SCI medio: ${"%.2f".format(meanSCI)}
                """.trimIndent()

                updateGraph(tempEntries)
                btnRefresh.isEnabled = true
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvAnalysisDetails.text = "Error al analizar datos."
                btnRefresh.isEnabled = true
            }
    }

    /**
     * 📈 Evolución de temperatura
     */
    private fun updateGraph(entries: List<Entry>) {
        if (entries.isEmpty()) return

        val dataSet = LineDataSet(entries, "Evolución Temperatura")
        dataSet.color = Color.parseColor("#673AB7")
        dataSet.setDrawCircles(false)
        dataSet.lineWidth = 2f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        lineChart.data = LineData(dataSet)
        lineChart.description.isEnabled = false
        lineChart.animateX(500)
        lineChart.invalidate()
    }
}
