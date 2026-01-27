package com.example.roommonitorapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BluetoothManager.BluetoothListener {

    private lateinit var statusText: TextView
    private lateinit var dataText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button

    private val bluetoothManager: BluetoothManager by lazy { BluetoothManager(this) }
    private val permissionHelper: PermissionHelper by lazy { PermissionHelper(this) }
    private val firebaseManager: FirebaseManager by lazy { FirebaseManager() }

    private var selectedDevice: BluetoothDevice? = null
    private var isUsingMockData = true

    // Datos sensores
    private var temperature = 0.0f
    private var humidity = 0.0f
    private var gasLevel = 0

    // Nombre real de nuestro DEVICE
    private val TARGET_DEVICE_NAME = "nRF52_Demo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeManagers()
        setupClickListeners()

        // Comentada por ahora
       // startMockData()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        dataText = findViewById(R.id.dataText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)

        connectButton.isEnabled = false
        updateSensorDisplay()
    }

    private fun initializeManagers() {
        bluetoothManager.setListener(this)
    }

    private fun setupClickListeners() {

        scanButton.setOnClickListener {
            if (bluetoothManager.isScanning()) {
                stopScan()
            } else {
                startBleScan()
            }
        }

        connectButton.setOnClickListener {
            selectedDevice?.let {
                if (bluetoothManager.isConnected()) {
                    disconnectDevice()
                } else {
                    connectToDevice(it)
                }
            } ?: Toast.makeText(this, "No hay dispositivo seleccionado", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun startBleScan() {
        if (!permissionHelper.hasAllRequiredPermissions()) {
            permissionHelper.requestAllPermissions()
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            statusText.text = "📱 Activa Bluetooth primero"
            enableBluetooth()
            return
        }

        bluetoothManager.startScan()
        statusText.text = "🔍 Escaneando BLE..."
        scanButton.text = "🛑 Detener Escaneo"
        connectButton.isEnabled = false
        selectedDevice = null
        dataText.text = "Buscando nRF52...\n\n"
    }

    private fun stopScan() {
        bluetoothManager.stopScan()
        statusText.text = "✅ Escaneo finalizado"
        scanButton.text = "🔍 Buscar dispositivo BLE"
        connectButton.isEnabled = selectedDevice != null
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        startActivityForResult(
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
            PermissionHelper.BLUETOOTH_REQUEST_CODE
        )
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopMockData()
        isUsingMockData = false

        bluetoothManager.connectToDevice(device)
        statusText.text = "🔗 Conectando a ${device.name}..."
        connectButton.isEnabled = false
        connectButton.text = "Conectando..."
    }

    private fun disconnectDevice() {
        bluetoothManager.disconnect()
        statusText.text = "🔌 Desconectado"
        connectButton.text = "🔗 Conectar al Sensor"
        connectButton.isEnabled = selectedDevice != null
        isUsingMockData = true
    }

    // 🔍 Dispositivos encontrados
    override fun onDevicesFound(devices: List<BluetoothDevice>) {
        runOnUiThread {
            val filtered = devices.filter {
                it.name != null && it.name.contains(TARGET_DEVICE_NAME)
            }

            if (filtered.isNotEmpty()) {
                selectedDevice = filtered.first()
                dataText.text = """
                    ✅ Dispositivo encontrado:
                    ${selectedDevice?.name}
                    MAC: ${selectedDevice?.address}
                """.trimIndent()
                connectButton.isEnabled = true
            }
        }
    }

    // 🔗 Estado conexión
    override fun onConnectionStateChanged(connected: Boolean, message: String) {
        runOnUiThread {
            statusText.text = message
            connectButton.text =
                if (connected) "🔌 Desconectar" else "🔗 Conectar al Sensor"
            connectButton.isEnabled = true
        }
    }

    // 📡 Datos reales desde nRF52
    override fun onSensorDataUpdated(temp: Float, hum: Float, gas: Int) {
        temperature = temp
        humidity = hum
        gasLevel = gas

        runOnUiThread { updateSensorDisplay() }

        firebaseManager.sendSensorData(temp, hum, gas)
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            statusText.text = "❌ Error BLE"
        }
    }

    // 🧪 Mock data
    private fun startMockData() {
        firebaseManager.startMockData { t, h, g ->
            temperature = t
            humidity = h
            gasLevel = g
            runOnUiThread { updateSensorDisplay() }
        }
    }

    private fun stopMockData() {
        firebaseManager.stopMockData()
    }

    private fun updateSensorDisplay() {
        val connected = bluetoothManager.isConnected()

        dataText.text = """
            🌡️ MONITOR AMBIENTAL
            
            Temperatura: ${"%.1f".format(temperature)} °C
            Humedad: ${"%.1f".format(humidity)} %
            Nivel de Gas: $gasLevel
            
            ${getAirQuality(gasLevel)}
            
            Estado: ${if (connected) "✅ Conectado" else "❌ Desconectado"}
            ${if (isUsingMockData) "🧪 Datos simulados" else "📡 nRF52 real"}
        """.trimIndent()
    }

    private fun getAirQuality(gas: Int): String =
        when {
            gas < 100 -> "✅ Aire excelente"
            gas < 300 -> "⚠️ Aire bueno"
            gas < 500 -> "🔶 Aire regular"
            else -> "🔴 Aire malo"
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE &&
            permissionHelper.handlePermissionResult(grantResults)
        ) {
            startBleScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionHelper.BLUETOOTH_REQUEST_CODE && resultCode == RESULT_OK) {
            startBleScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.cleanup()
        stopMockData()
    }
}
