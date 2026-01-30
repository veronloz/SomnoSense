package com.example.roommonitorapp

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BluetoothManager.BluetoothListener {

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var historyButton: Button

    private lateinit var statisticsButton: Button
    private lateinit var localStatisticsButton: Button

    private lateinit var gasCO: TextView
    private lateinit var gasNO2: TextView
    private lateinit var gasNH3: TextView
    private lateinit var gasCH4: TextView
    private lateinit var gasETOH: TextView

    private lateinit var cardTemp: TextView
    private lateinit var cardHum: TextView
    private lateinit var cardSound: TextView

    private val bluetoothManager by lazy { BluetoothManager(this) }
    private val firebaseManager by lazy { FirebaseManager() }

    private var selectedDevice: BluetoothDevice? = null

    private var co = 0f
    private var no2 = 0f
    private var nh3 = 0f
    private var ch4 = 0f
    private var etoh = 0f

    private var temperature = 0f
    private var humidity = 0f
    private var soundCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)
        historyButton = findViewById(R.id.btnHistory)
        statisticsButton = findViewById(R.id.btnStatistics)
        localStatisticsButton = findViewById(R.id.btnLocalStatistics)

        gasCO = findViewById(R.id.gasCO)
        gasNO2 = findViewById(R.id.gasNO2)
        gasNH3 = findViewById(R.id.gasNH3)
        gasCH4 = findViewById(R.id.gasCH4)
        gasETOH = findViewById(R.id.gasETOH)

        cardTemp = findViewById(R.id.cardTemp)
        cardHum = findViewById(R.id.cardHum)
        cardSound = findViewById(R.id.cardSound)

        cardTemp.text = "🌡 Temp\n-- °C"
        cardHum.text = "💧 Hum\n-- %"
        cardSound.text = "🔊 Sound\n0"

        bluetoothManager.setListener(this)

        scanButton.setOnClickListener {
            bluetoothManager.startScan()
            statusText.text = "🔍 Escaneando BLE..."
        }

        connectButton.setOnClickListener {
            selectedDevice?.let { bluetoothManager.connectToDevice(it) }
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        statisticsButton.setOnClickListener {
            startActivity(Intent(this, AnalysisActivity::class.java))
        }

        localStatisticsButton.setOnClickListener {
            startActivity(Intent(this, LocalAnalysis::class.java))
        }

        updateAllGasCards()
    }

    // ================= BLE CALLBACKS =================

    override fun onDevicesFound(devices: List<BluetoothDevice>) {
        selectedDevice = devices.firstOrNull()
        if (selectedDevice != null) {
            statusText.text = "✅ Dispositivo encontrado"
            connectButton.isEnabled = true
        }
    }

    override fun onConnectionStateChanged(connected: Boolean, message: String) {
        statusText.text = message
    }

    override fun onGasDataUpdated(
        co: Float,
        no2: Float,
        nh3: Float,
        ch4: Float,
        etoh: Float
    ) {
        this.co = co
        this.no2 = no2
        this.nh3 = nh3
        this.ch4 = ch4
        this.etoh = etoh

        runOnUiThread {
            updateAllGasCards()
        }
        // ❌ NO Firebase aquí
    }

    override fun onEnvDataUpdated(temp: Float, humidity: Float) {
        this.temperature = temp
        this.humidity = humidity

        runOnUiThread {
            updateEnvCard(cardTemp, "🌡 Temp", temp, 18f, 28f, "°C")
            updateEnvCard(cardHum, "💧 Hum", humidity, 30f, 70f, "%")
        }

        // ✅ ÚNICO punto de escritura en Firebase
        firebaseManager.sendSensorData(
            co, no2, nh3, ch4, etoh,
            temperature, humidity,
            soundCount
        )
    }

    override fun onSoundDetected(count: Int) {
        soundCount = count

        runOnUiThread {
            cardSound.text = "🔊 Sound\n$count"
        }
    }

    override fun onError(message: String) {
        statusText.text = "❌ $message"
    }

    // ================= UI HELPERS =================

    private fun updateAllGasCards() {
        updateGasCard(gasCO, "CO", co)
        updateGasCard(gasNO2, "NO₂", no2)
        updateGasCard(gasNH3, "NH₃", nh3)
        updateGasCard(gasCH4, "CH₄", ch4)
        updateGasCard(gasETOH, "C₂H₅OH", etoh)
    }

    private fun updateGasCard(view: TextView, label: String, value: Float) {
        view.text = "$label\n${"%.2f".format(value)} ppm"
    }

    private fun updateEnvCard(
        view: TextView,
        label: String,
        value: Float,
        low: Float,
        high: Float,
        unit: String
    ) {
        view.text = "$label\n${"%.1f".format(value)} $unit"
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.cleanup()
    }
}
