package com.example.roommonitorapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BluetoothManager.BluetoothListener {

    private lateinit var statusText: TextView
    private lateinit var dataText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button

    private val bluetoothManager by lazy { BluetoothManager(this) }
    private val firebaseManager by lazy { FirebaseManager() }

    private var selectedDevice: BluetoothDevice? = null

    // Gases
    private var co = 0f
    private var no2 = 0f
    private var nh3 = 0f
    private var ch4 = 0f
    private var etoh = 0f

    // Environmental Data
    private var temperature = 0f
    private var humidity = 0f
    private var soundCount = 0

    // Asegúrate que este nombre sea EXACTAMENTE el que configuraste en el nRF52
    private val TARGET_DEVICE_NAME = "nRF52_Demo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        dataText = findViewById(R.id.dataText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)

        connectButton.isEnabled = false // Deshabilitado hasta encontrar el sensor

        bluetoothManager.setListener(this)

        scanButton.setOnClickListener {
            selectedDevice = null
            connectButton.isEnabled = false
            bluetoothManager.startScan()
            statusText.text = "🔍 Buscando '$TARGET_DEVICE_NAME'..."
        }

        connectButton.setOnClickListener {
            selectedDevice?.let {
                bluetoothManager.stopScan()
                bluetoothManager.connectToDevice(it)
            }
        }

        updateSensorDisplay()
    }

    @SuppressLint("MissingPermission")
    override fun onDevicesFound(devices: List<BluetoothDevice>) {
        // Buscamos en la lista el dispositivo que tenga el nombre correcto
        val sensor = devices.find { it.name == TARGET_DEVICE_NAME }
        
        if (sensor != null && selectedDevice == null) {
            selectedDevice = sensor
            runOnUiThread {
                statusText.text = "✅ Sensor '$TARGET_DEVICE_NAME' encontrado"
                connectButton.isEnabled = true
            }
        }
    }

    override fun onConnectionStateChanged(connected: Boolean, message: String) {
        runOnUiThread {
            statusText.text = message
            updateSensorDisplay()
        }
    }

    override fun onGasDataUpdated(co: Float, no2: Float, nh3: Float, ch4: Float, etoh: Float) {
        this.co = co
        this.no2 = no2
        this.nh3 = nh3
        this.ch4 = ch4
        this.etoh = etoh
        runOnUiThread { updateSensorDisplay() }
        firebaseManager.sendGasData(co, no2, nh3, ch4, etoh)
    }

    override fun onEnvDataUpdated(temp: Float, humidity: Float) {
        this.temperature = temp
        this.humidity = humidity
        runOnUiThread { updateSensorDisplay() }
    }

    override fun onSoundDetected(count: Int) {
        this.soundCount = count
        runOnUiThread { updateSensorDisplay() }
    }

    override fun onError(message: String) {
        runOnUiThread {
            statusText.text = "❌ $message"
        }
    }

    private fun updateSensorDisplay() {
        dataText.text = """
            🧪 MONITOR DE GASES
            
            CO: ${"%.2f".format(co)} ppm
            NO₂: ${"%.2f".format(no2)} ppm
            NH₃: ${"%.2f".format(nh3)} ppm
            CH₄: ${"%.2f".format(ch4)} ppm
            C₂H₅OH: ${"%.2f".format(etoh)} ppm
            
            🌡️ AMBIENTE
            Temp: ${"%.1f".format(temperature)}°C
            Humedad: ${"%.1f".format(humidity)}%
            
            🔊 RUIDO
            Alertas: $soundCount
            
            Estado: ${if (bluetoothManager.isConnected()) "✅ Conectado" else "❌ Desconectado"}
            """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.cleanup()
    }
}
