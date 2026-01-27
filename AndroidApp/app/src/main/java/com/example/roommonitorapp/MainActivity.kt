package com.example.roommonitorapp

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

    // Nombre real de nuestro DEVICE
    private val TARGET_DEVICE_NAME = "nRF52_Demo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        dataText = findViewById(R.id.dataText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)

        bluetoothManager.setListener(this)

        scanButton.setOnClickListener {
            bluetoothManager.startScan()
            statusText.text = "🔍 Escaneando BLE..."
        }

        connectButton.setOnClickListener {
            selectedDevice?.let {
                bluetoothManager.connectToDevice(it)
            }
        }

        updateSensorDisplay()
    }

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
            updateSensorDisplay()
        }

        firebaseManager.sendGasData(co, no2, nh3, ch4, etoh)
    }

    override fun onError(message: String) {
        statusText.text = "❌ $message"
    }

    private fun updateSensorDisplay() {
        dataText.text = """
            🧪 GAS MONITOR
            
            CO: ${"%.2f".format(co)} ppm
            NO₂: ${"%.2f".format(no2)} ppm
            NH₃: ${"%.2f".format(nh3)} ppm
            CH₄: ${"%.2f".format(ch4)} ppm
            C₂H₅OH: ${"%.2f".format(etoh)} ppm
            
            Estado: ${if (bluetoothManager.isConnected()) "✅ Conectado" else "❌ Desconectado"}
            """.trimIndent()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.cleanup()
    }
}
