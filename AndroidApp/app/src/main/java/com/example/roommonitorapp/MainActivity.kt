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
    private lateinit var connectButton: TextView

    // Inicializar las propiedades inmediatamente en lugar de usar lateinit
    private val bluetoothManager: BluetoothManager by lazy { BluetoothManager(this) }
    private val permissionHelper: PermissionHelper by lazy { PermissionHelper(this) }
    private val firebaseManager: FirebaseManager by lazy { FirebaseManager() }

    private var selectedDevice: BluetoothDevice? = null
    private var isUsingMockData = true

    // Variables para almacenar lecturas de sensores
    private var temperature: Float = 0.0f
    private var humidity: Float = 0.0f
    private var gasLevel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()

        // Inicializar managers después de las vistas
        initializeManagers()

        // Iniciar datos mock por defecto
        startMockData()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        dataText = findViewById(R.id.dataText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)

        connectButton.isEnabled = false

        // Mostrar estado inicial sin depender de bluetoothManager
        updateSensorDisplay()
    }

    private fun initializeManagers() {
        // Configurar el listener después de que bluetoothManager esté inicializado
        bluetoothManager.setListener(this)

        // Firebase se inicializa automáticamente, no necesita initialize()
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
            if (bluetoothManager.isConnected()) {
                disconnectDevice()
            } else {
                selectedDevice?.let { device ->
                    connectToDevice(device) // 3. Conectar
                } ?: run {
                    Toast.makeText(this, "Primero escanea y encuentra dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Historial
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startBleScan() {
        if (!permissionHelper.hasAllRequiredPermissions()) {
            permissionHelper.requestAllPermissions()
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            statusText.text = "📱 Activa Bluetooth primero"
            enableBluetooth()
            return
        }
        // 1. Escanear dispositivos
        bluetoothManager.startScan()
        statusText.text = "🔍 Escaneando BLE..."
        scanButton.text = "🛑 Detener Escaneo"
        connectButton.isEnabled = false
        dataText.text = "Buscando dispositivos...\n\n"
    }

    private fun stopScan() {
        bluetoothManager.stopScan()
        statusText.text = "✅ Escaneo terminado"
        scanButton.text = "🔍 Buscar Dispositivos BLE"

        if (dataText.text.toString().contains("MAC:")) {
            connectButton.isEnabled = true
            connectButton.text = "🔗 Conectar al Sensor"
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, PermissionHelper.BLUETOOTH_REQUEST_CODE)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopMockData() // Detener datos mock al conectar dispositivo real
        isUsingMockData = false

        bluetoothManager.connectToDevice(device)
        statusText.text = "🔗 Conectando a sensor..."
        connectButton.isEnabled = false
        connectButton.text = "Conectando..."
    }

    private fun disconnectDevice() {
        bluetoothManager.disconnect()
        statusText.text = "🔌 Desconectando..."
        connectButton.isEnabled = false

        // Volver a datos mock si se desconecta
        startMockData()
        isUsingMockData = true
    }

    // 2. Mostrar dispositivos encontrados
    override fun onDevicesFound(devices: List<BluetoothDevice>) {
        runOnUiThread {
            val currentText = dataText.text.toString()
            val newDeviceInfo = devices.joinToString("\n") { device ->
                "• ${device.name ?: "Sin nombre"}\n  MAC: ${device.address}\n"
            }

            if (currentText.startsWith("Buscando dispositivos...")) {
                dataText.text = newDeviceInfo
                selectedDevice = devices.firstOrNull()
            } else {
                dataText.text = currentText + newDeviceInfo
                if (selectedDevice == null) {
                    selectedDevice = devices.firstOrNull()
                }
            }
        }
    }

    override fun onConnectionStateChanged(connected: Boolean, message: String) {
        runOnUiThread {
            statusText.text = message
            connectButton.isEnabled = true
            connectButton.text = if (connected) "🔌 Desconectar" else "🔗 Conectar al Sensor"
        }
    }

    // 4. Recibir datos reales
        override fun onSensorDataUpdated(temperature: Float, humidity: Float, gasLevel: Int) {
        this.temperature = temperature
        this.humidity = humidity
        this.gasLevel = gasLevel

        runOnUiThread {
            updateSensorDisplay()
        }

        // Enviar a Firebase
        firebaseManager.sendSensorData(temperature, humidity, gasLevel)
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            statusText.text = "❌ Error"
        }
    }

    // Mock data management
    private fun startMockData() {
        firebaseManager.startMockData { temp, hum, gas ->
            this.temperature = temp
            this.humidity = hum
            this.gasLevel = gas

            runOnUiThread {
                updateSensorDisplay()
            }
        }
    }

    private fun stopMockData() {
        firebaseManager.stopMockData()
    }

    private fun updateSensorDisplay() {
        // Verificar si bluetoothManager está inicializado antes de usarlo
        val isConnected = try {
            bluetoothManager.isConnected()
        } catch (e: UninitializedPropertyAccessException) {
            false
        }

        val sensorText = """
            🌡️ MONITOR AMBIENTAL 🌡️
            
            Temperatura: ${"%.1f".format(temperature)} °C
            Humedad: ${"%.1f".format(humidity)} %
            Nivel de Gas: $gasLevel
            
            ${getAirQuality(gasLevel)}
            
            Estado: ${if (isConnected) "✅ Conectado" else "❌ Desconectado"}
            ${if (isUsingMockData) "🧪 Usando datos de prueba" else "📱 Dispositivo real"}
        """.trimIndent()

        dataText.text = sensorText
    }

    private fun getAirQuality(gasLevel: Int): String {
        return when {
            gasLevel < 100 -> "✅ Calidad del aire: Excelente"
            gasLevel < 300 -> "⚠️ Calidad del aire: Buena"
            gasLevel < 500 -> "🔶 Calidad del aire: Regular"
            else -> "🔴 Calidad del aire: Mala"
        }
    }

    // Permission handling
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (permissionHelper.handlePermissionResult(grantResults)) {
                startBleScan()
            }
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