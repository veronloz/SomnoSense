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
import com.google.firebase.functions.FirebaseFunctions
class AnalysisActivity : AppCompatActivity() {


    private lateinit var tvSleepScore: TextView
    private lateinit var tvAnalysisDetails: TextView
    private lateinit var lineChart: LineChart
    private lateinit var btnRefresh: Button

    private val database = FirebaseDatabase.getInstance("https://somnosense-default-rtdb.europe-west1.firebasedatabase.app/")
        .getReference("somnosense/data")
    private val functions = FirebaseFunctions.getInstance("europe-west1")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        tvSleepScore = findViewById(R.id.tvSleepScore)
        tvAnalysisDetails = findViewById(R.id.tvAnalysisDetails)
        lineChart = findViewById(R.id.lineChart)
        btnRefresh = findViewById(R.id.btnRefreshAnalysis)

        btnRefresh.setOnClickListener {
            runFullAnalysis()
        }

        runFullAnalysis()
    }


    private fun runFullAnalysis() {
        btnRefresh.isEnabled = false
        tvAnalysisDetails.text = "Calculando análisis..."

        functions.getHttpsCallable("calculate_sleep_score_on_demand")
            .call()
            .addOnSuccessListener { result ->
                // Use getData() instead of .data
                val data = result.getData() as? Map<String, Any>

                if (data != null) {
                    val score = data["score"] ?: "N/A"
                    tvSleepScore.text = "Puntaje: $score/100"

                    val avgTemp = data["avg_temp"] ?: "--"
                    val avgHum = data["avg_hum"] ?: "--"
                    val avgCo = data["avg_co"] ?: "--"
                    val avgSound = data["avg_sound"] ?: "--"

                    tvAnalysisDetails.text = """
                Promedios (Últimas 1000 lecturas):
                🌡 Temp: $avgTemp°C
                💧 Hum: $avgHum%
                💨 Gas CO: $avgCo ppm
                🔊 Ruido: $avgSound
            """.trimIndent()

                    updateGraph()
                } else {
                    tvAnalysisDetails.text = "El servidor no devolvió datos."
                }
                btnRefresh.isEnabled = true
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvAnalysisDetails.text = "Error al obtener análisis."
                btnRefresh.isEnabled = true
            }
    }

    private fun updateGraph() {
        database.limitToLast(1000).get().addOnSuccessListener { snapshot ->
            val tempEntries = ArrayList<Entry>()
            var index = 0f

            for (child in snapshot.children) {
                // Pulling temperature for the evolution graph
                val env = child.child("environment")
                val temp = env.child("temp").getValue(Double::class.java)?.toFloat() ?: 0f
                tempEntries.add(Entry(index, temp))
                index++
            }

            if (tempEntries.isEmpty()) return@addOnSuccessListener

            val dataSet = LineDataSet(tempEntries, "Evolución Temp")
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
}