package com.example.roommonitorapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.roommonitorapp.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity(), BluetoothManager.BluetoothListener {

    // ViewBinding: La forma moderna y segura de acceder a las vistas
    private lateinit var binding: ActivityMainBinding

    private val bluetoothManager: BluetoothManager by lazy { BluetoothManager(this) }
    private val permissionHelper: PermissionHelper by lazy { PermissionHelper(this) }
    private val firebaseManager: FirebaseManager by lazy { FirebaseManager() }

    private var selectedDevice: BluetoothDevice? = null
    private var isUsingMockData = true

    // Datos del sensor
    private var temperature: Float = 0.0f
    private var humidity: Float = 0.0f
    private var gasLevel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()

        // Inyectar el listener en tu Manager
        bluetoothManager.setListener(this)

        // Comenzar con datos de prueba hasta que haya conexión real
        startMockData()
    }

    private fun setupUI() {
        // Configuramos la fuente (asegúrate de haber importado @font/nunito)
        // binding.dataText.typeface = ResourcesCompat.getFont(this, R.font.nunito)

        binding.connectButton.isEnabled = false
        updateStatusUI("Esperando conexión BLE...", "🔵",
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer))
    }

    private fun setupClickListeners() {
        binding.scanButton.setOnClickListener {
            if (bluetoothManager.isScanning()) {
                stopScan()
            } else {
                startBleScan()
            }
        }

        binding.connectButton.setOnClickListener {
            if (bluetoothManager.isConnected()) {
                disconnectDevice()
            } else {
                selectedDevice?.let { device ->
                    connectToDevice(device)
                } ?: showToast("Escanea para encontrar dispositivos primero")
            }
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    // --- LÓGICA DE BLUETOOTH ---

    private fun startBleScan() {
        if (!permissionHelper.hasAllRequiredPermissions()) {
            permissionHelper.requestAllPermissions()
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            updateStatusUI("Activa el Bluetooth", "📱", Color.parseColor("#EF5350"))
            return
        }

        bluetoothManager.startScan()
        updateStatusUI("Buscando sensores...", "🔍", Color.parseColor("#42A5F5"))
        binding.scanButton.text = "Detener"
    }

    private fun stopScan() {
        bluetoothManager.stopScan()
        binding.scanButton.text = "Buscar BLE"
        if (selectedDevice == null) {
            updateStatusUI("Escaneo terminado", "🔵", Color.GRAY)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopMockData()
        isUsingMockData = false
        bluetoothManager.connectToDevice(device)
        updateStatusUI("Conectando...", "⏳", Color.parseColor("#FFA726"))
        binding.connectButton.isEnabled = false
    }

    private fun disconnectDevice() {
        bluetoothManager.disconnect()
        updateStatusUI("Desconectando...", "🔌", Color.GRAY)
        startMockData()
        isUsingMockData = true
    }

    // --- CALLBACKS DEL BLUETOOTH LISTENER ---

    override fun onDevicesFound(devices: List<BluetoothDevice>) {
        runOnUiThread {
            selectedDevice = devices.firstOrNull()
            if (selectedDevice != null) {
                binding.dataText.text = "Dispositivo listo:\n${selectedDevice?.name ?: "Sensor Room"}\nMAC: ${selectedDevice?.address}"
                binding.connectButton.isEnabled = true
                updateStatusUI("Dispositivo encontrado", "📍", Color.parseColor("#66BB6A"))
            }
        }
    }

    override fun onConnectionStateChanged(connected: Boolean, message: String) {
        runOnUiThread {
            if (connected) {
                updateStatusUI("Conectado", "🟢", Color.parseColor("#43A047"))
                binding.connectButton.text = "Desconectar"
                binding.connectButton.isEnabled = true
            } else {
                updateStatusUI("Desconectado", "🔴", Color.parseColor("#E53935"))
                binding.connectButton.text = "Conectar"
                binding.connectButton.isEnabled = true
            }
        }
    }

    override fun onSensorDataUpdated(temp: Float, hum: Float, gas: Int) {
        this.temperature = temp
        this.humidity = hum
        this.gasLevel = gas

        runOnUiThread { updateSensorDisplay() }

        // Sincronización Pro con Firebase
        firebaseManager.sendSensorData(temp, hum, gas)
    }

    override fun onError(message: String) {
        runOnUiThread {
            showToast(message)
            updateStatusUI("Error de sensor", "⚠️", Color.RED)
        }
    }

    // --- UI HELPERS ---

    private fun updateStatusUI(msg: String, icon: String, color: Int) {
        binding.statusText.text = msg
        binding.statusIcon.text = icon
        binding.statusCard.setCardBackgroundColor(color)
        // Aseguramos legibilidad del texto
        binding.statusText.setTextColor(if (color == Color.GRAY) Color.BLACK else Color.WHITE)
    }

    private fun updateSensorDisplay() {
        val quality = getAirQuality(gasLevel)

        // Uso de Monospace para los valores numéricos para que se vean alineados
        val display = """
            [ DATOS EN TIEMPO REAL ]
            
            TEMPERATURA : ${"%.1f".format(temperature)} °C
            HUMEDAD     : ${"%.1f".format(humidity)} %
            NIVEL GAS   : $gasLevel
            
            CALIDAD AIRE: $quality
            
            ORIGEN: ${if (isUsingMockData) "🧪 MODO SIMULACIÓN" else "📡 SENSOR FÍSICO"}
        """.trimIndent()

        binding.dataText.text = display
    }

    private fun getAirQuality(gas: Int): String = when {
        gas < 100 -> "EXCELENTE ✨"
        gas < 300 -> "ACEPTABLE 👍"
        else -> "MALA (VENTILAR) 🚨"
    }

    private fun startMockData() {
        firebaseManager.startMockData { t, h, g ->
            this.temperature = t
            this.humidity = h
            this.gasLevel = g
            runOnUiThread { updateSensorDisplay() }
        }
    }

    private fun stopMockData() = firebaseManager.stopMockData()

    private fun showToast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.cleanup()
        stopMockData()
    }
}