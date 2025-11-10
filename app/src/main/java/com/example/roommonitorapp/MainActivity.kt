package com.example.roommonitorapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var dataText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button

    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var isConnected = false
    private var selectedDevice: BluetoothDevice? = null

    private val handler = Handler(Looper.getMainLooper())

    // --- UUIDs actualizados para coincidir con tu main.c de Zephyr ---
    // Servicio: "GasSensorSvc" (47617353-656e-736f-7253-766300000000)
    private val GAS_SENSOR_SERVICE_UUID = UUID.fromString("47617353-656e-736f-7253-766300000000")
    // Característica: "GasReadings" (47617352-6561-6469-6e67-73000000000)
    private val GAS_READINGS_CHARACTERISTIC_UUID = UUID.fromString("47617352-6561-6469-6e67-73000000000")
    // UUID del Descriptor CCC (Client Characteristic Configuration)
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    // ---------------------------------------------------------------


    // Variables para almacenar lecturas de gases
    private var co: Float = 0.0f
    private var no2: Float = 0.0f
    private var nh3: Float = 0.0f
    private var ch4: Float = 0.0f
    private var etoh: Float = 0.0f


    private companion object {
        const val TAG = "RoomMonitor"
        const val PERMISSION_REQUEST_CODE = 100
        const val BLUETOOTH_REQUEST_CODE = 101
        const val SCAN_PERIOD: Long = 10000
        // const val READING_INTERVAL: Long = 2000 // No es necesario, usaremos notificaciones
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        checkAndInitializeBluetooth()
        setupClickListeners()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        dataText = findViewById(R.id.dataText)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)

        connectButton.isEnabled = false
        updateSensorDisplay() // Mostrar estado inicial
    }

    private fun checkAndInitializeBluetooth() {
        Log.d(TAG, "Inicializando Bluetooth...")

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            statusText.text = "❌ Bluetooth no disponible"
            scanButton.isEnabled = false
            connectButton.isEnabled = false
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            statusText.text = "📱 Activa Bluetooth primero"
            enableBluetooth()
            return
        }

        Log.d(TAG, "Bluetooth activado, verificando permisos...")
        checkPermissionsAndSetup()
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        Log.d(TAG, "Solicitando activación de Bluetooth")
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        try {
            startActivityForResult(enableBtIntent, BLUETOOTH_REQUEST_CODE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al activar Bluetooth: ${e.message}")
            statusText.text = "❌ Error de permisos"
            requestAllPermissions()
        }
    }

    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startBleScan()
            }
        }

        connectButton.setOnClickListener {
            if (isConnected) {
                disconnectDevice()
            } else {
                selectedDevice?.let { device ->
                    connectToDevice(device)
                } ?: run {
                    Toast.makeText(this, "Primero escanea y encuentra dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startBleScan() {
        Log.d(TAG, "Intentando iniciar escaneo BLE")

        if (!hasAllRequiredPermissions()) {
            Log.d(TAG, "Faltan permisos, solicitando...")
            requestAllPermissions()
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            statusText.text = "❌ Bluetooth no disponible"
            enableBluetooth()
            return
        }

        if (bleScanner == null) {
            bleScanner = bluetoothAdapter!!.bluetoothLeScanner
            if (bleScanner == null) {
                statusText.text = "❌ Error: No se puede crear escáner BLE"
                return
            }
        }

        startScanning()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        isScanning = true
        statusText.text = "🔍 Escaneando BLE..."
        scanButton.text = "🛑 Detener Escaneo"
        connectButton.isEnabled = false
        dataText.text = "Buscando dispositivos...\n\n"

        Log.d(TAG, "Iniciando escaneo BLE")

        // Auto-detener después de 10 segundos
        handler.postDelayed({
            if (isScanning) {
                Log.d(TAG, "Auto-deteniendo escaneo por tiempo")
                stopScan()
            }
        }, SCAN_PERIOD)

        // Iniciar escaneo
        try {
            bleScanner!!.startScan(scanCallback)
            Toast.makeText(this, "Escaneo iniciado por 10 segundos", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Escaneo BLE iniciado correctamente")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de seguridad al escanear: ${e.message}")
            statusText.text = "❌ Error de permisos al escanear"
            stopScan()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al escanear: ${e.message}")
            statusText.text = "❌ Error al escanear"
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        Log.d(TAG, "Deteniendo escaneo BLE")
        isScanning = false
        scanButton.text = "🔍 Buscar Dispositivos BLE"
        statusText.text = "✅ Escaneo terminado"

        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener escaneo: ${e.message}")
        }

        handler.removeCallbacksAndMessages(null)

        // Habilitar botón de conectar si hay dispositivos encontrados
        if (dataText.text.toString().contains("MAC:") && !dataText.text.toString().startsWith("Buscando dispositivos...")) {
            connectButton.isEnabled = true
            connectButton.text = "🔗 Conectar al Sensor"
        }
    }

    // Callback para resultados BLE
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                processScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Error en escaneo BLE: $errorCode")
            runOnUiThread {
                statusText.text = "❌ Error: $errorCode"
                when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> dataText.text = "Error: Escaneo ya activo"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> dataText.text = "Error: App no registrada"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> dataText.text = "Error interno"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> dataText.text = "BLE no soportado"
                    else -> dataText.text = "Error desconocido: $errorCode"
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun processScanResult(result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Sin nombre"
            val deviceAddress = device.address
            val rssi = result.rssi

            Log.d(TAG, "Dispositivo encontrado: $deviceName - $deviceAddress - RSSI: $rssi")

            runOnUiThread {
                val currentText = dataText.text.toString()

                // Formato más compacto
                val newDeviceInfo = "• $deviceName\n  MAC: $deviceAddress\n  Señal: $rssi dBm\n"

                // Si es el primer dispositivo, reemplazar el texto inicial
                if (currentText.startsWith("Buscando dispositivos...") ||
                    currentText.startsWith("Datos del sensor aparecerán aquí.")) {
                    dataText.text = newDeviceInfo
                    // Guardar el primer dispositivo encontrado para conexión
                    selectedDevice = device
                } else {
                    // Solo agregar si no existe ya
                    if (!currentText.contains(deviceAddress)) {
                        dataText.text = currentText + newDeviceInfo
                        // Si es el primer dispositivo, guardarlo para conexión
                        if (selectedDevice == null) {
                            selectedDevice = device
                        }
                    }
                }
            }
        }
    }

    // CONEXIÓN AL DISPOSITIVO
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        statusText.text = "🔗 Conectando a sensor..."
        connectButton.isEnabled = false
        connectButton.text = "Conectando..."

        // Guardar el dispositivo seleccionado
        selectedDevice = device

        // Cerrar conexión anterior si existe
        bluetoothGatt?.close()

        // Conectar al dispositivo
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        Toast.makeText(this, "Conectando a: ${device.name ?: "Sensor de Gas"}", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Iniciando conexión GATT con: ${device.address}")
    }

    // Callback para la conexión GATT
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "Estado de conexión cambiado: $newState, status: $status")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado al dispositivo GATT")
                    runOnUiThread {
                        statusText.text = "✅ Conectado - Descubriendo servicios..."
                        connectButton.text = "🔌 Desconectar"
                        connectButton.isEnabled = true
                    }
                    isConnected = true
                    // Descubrir servicios
                    handler.post {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado del dispositivo GATT")
                    runOnUiThread {
                        statusText.text = "❌ Desconectado"
                        connectButton.text = "🔗 Conectar al Sensor"
                        connectButton.isEnabled = true
                    }
                    isConnected = false
                    // stopSensorReadings() // No es necesario
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "Servicios descubiertos, status: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    statusText.text = "✅ Conectado - Configurando sensor"
                }

                // Configurar lecturas de sensores
                setupSensorReadings(gatt)

            } else {
                Log.e(TAG, "Error al descubrir servicios: $status")
                runOnUiThread {
                    statusText.text = "❌ Error descubriendo servicios"
                    dataText.text = "Error al descubrir servicios: $status\n\n" +
                            "Mostrando todos los servicios disponibles:"
                    discoverAllServices(gatt) // Mostrar para debugging
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processSensorData(characteristic)
            } else {
                Log.e(TAG, "Error leyendo característica: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Datos en tiempo real vía notificaciones (¡esto es lo que queremos!)
            processSensorData(characteristic)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor CCC escrito, notificaciones habilitadas")
                runOnUiThread {
                    statusText.text = "✅ Sensor configurado. Recibiendo datos..."
                }
            } else {
                Log.e(TAG, "Error al escribir descriptor CCC: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupSensorReadings(gatt: BluetoothGatt) {
        val gasService = gatt.getService(GAS_SENSOR_SERVICE_UUID)
        val gasChar = gasService?.getCharacteristic(GAS_READINGS_CHARACTERISTIC_UUID)

        if (gasChar != null) {
            // 1. Habilitar notificaciones localmente
            gatt.setCharacteristicNotification(gasChar, true)

            // 2. Escribir en el descriptor CCC para habilitar notificaciones en el dispositivo
            val cccDescriptor = gasChar.getDescriptor(CCC_DESCRIPTOR_UUID)
            if (cccDescriptor != null) {
                cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccDescriptor)
                Log.d(TAG, "Habilitando notificaciones para Gas Readings...")
            } else {
                Log.w(TAG, "No se encontró el descriptor CCC")
            }

            // Opcional: Leer el valor inicial
            gatt.readCharacteristic(gasChar)
            Log.d(TAG, "Sensor de gas configurado")

        } else {
            Log.w(TAG, "Sensor de gas (Servicio o Característica) NO encontrado")
            runOnUiThread {
                statusText.text = "⚠️ No se encontraron sensores conocidos"
                dataText.text = "No se encontraron los sensores esperados.\n\n" +
                        "Mostrando todos los servicios disponibles para debugging:"
                discoverAllServices(gatt) // Mostrar todos los servicios para debugging
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverAllServices(gatt: BluetoothGatt) {
        val services = gatt.services
        runOnUiThread {
            var servicesInfo = "📊 SERVICIOS ENCONTRADOS:\n\n"
            services.forEach { service ->
                servicesInfo += "🔧 SERVICIO: ${service.uuid}\n"
                val characteristics = service.characteristics
                characteristics.forEach { char ->
                    val properties = getCharacteristicProperties(char.properties)
                    servicesInfo += "  └─ 📊 Característica: ${char.uuid}\n"
                    servicesInfo += "      Propiedades: $properties\n\n"
                }
                servicesInfo += "\n"
            }
            // Agregar al texto existente en lugar de reemplazar
            val currentText = dataText.text.toString()
            dataText.text = "$currentText\n\n$servicesInfo"
        }
        Log.d(TAG, "Encontrados ${services.size} servicios")
    }

    private fun getCharacteristicProperties(properties: Int): String {
        val props = mutableListOf<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
        return if (props.isEmpty()) "NONE" else props.joinToString(", ")
    }

    private fun processSensorData(characteristic: BluetoothGattCharacteristic) {
        when (characteristic.uuid) {
            GAS_READINGS_CHARACTERISTIC_UUID -> {
                parseGasData(characteristic.value)
                Log.d(TAG, "Datos de gas recibidos")
            }
            else -> {
                Log.d(TAG, "Datos de característica desconocida: ${characteristic.uuid}")
            }
        }

        runOnUiThread {
            updateSensorDisplay()
        }
    }

    private fun parseGasData(data: ByteArray?) {
        // Tu main.c envía 5 floats (5 * 4 bytes = 20 bytes)
        if (data == null || data.size < 20) {
            Log.w(TAG, "Datos de gas inválidos, tamaño: ${data?.size}")
            return
        }

        try {
            // El buffer debe estar en Little Endian, como en tu nRF52
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            co = buffer.float
            no2 = buffer.float
            nh3 = buffer.float
            ch4 = buffer.float
            etoh = buffer.float

            Log.d(TAG, "Parseado: CO=$co, NO2=$no2, NH3=$nh3, CH4=$ch4, EtOH=$etoh")

        } catch (e: Exception) {
            Log.e(TAG, "Error parseando datos de gas: ${e.message}")
        }
    }

    private fun updateSensorDisplay() {
        // Mostrar los 5 valores de gas
        val sensorText = """
            ⛽ MONITOR DE GAS (nRF52) ⛽
            
            CO:     ${"%.2f".format(co)} ppm
            NO2:    ${"%.2f".format(no2)} ppm
            NH3:    ${"%.2f".format(nh3)} ppm
            CH4:    ${"%.2f".format(ch4)} ppm
            C2H5OH: ${"%.2f".format(etoh)} ppm
            
            Estado: ${if (isConnected) "✅ Conectado" else "❌ Desconectado"}
        """.trimIndent()

        dataText.text = sensorText
    }


    @SuppressLint("MissingPermission")
    private fun disconnectDevice() {
        Log.d(TAG, "Desconectando dispositivo")
        statusText.text = "🔌 Desconectando..."
        connectButton.isEnabled = false

        // stopSensorReadings() // No es necesario
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        selectedDevice = null
        isConnected = false

        runOnUiThread {
            statusText.text = "✅ Desconectado"
            connectButton.text = "🔗 Conectar al Sensor"
            connectButton.isEnabled = true
        }
    }

    // --- El resto del código de permisos y ciclo de vida (sin cambios) ---

    // Verificar permisos
    private fun hasAllRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun checkPermissionsAndSetup() {
        if (hasAllRequiredPermissions()) {
            bleScanner = bluetoothAdapter!!.bluetoothLeScanner
            statusText.text = "✅ Listo para escanear BLE"
            scanButton.isEnabled = true
            Log.d(TAG, "Configuración BLE completada correctamente")
        } else {
            statusText.text = "👆 Pulsa para permisos BLE"
            scanButton.isEnabled = true
        }
    }

    private fun requestAllPermissions() {
        val permissions = getRequiredPermissions()
        Log.d(TAG, "Solicitando permisos: ${permissions.joinToString()}")

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("Permisos necesarios para BLE")
                .setMessage("Para escanear y conectar dispositivos Bluetooth necesitamos:\n\n• Ubicación (para encontrar dispositivos)\n• Permisos de Bluetooth\n\nEstos permisos son esenciales para la funcionalidad BLE.")
                .setPositiveButton("Entendido") { _, _ ->
                    ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    statusText.text = "❌ Permisos necesarios"
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                statusText.text = "✅ Permisos concedidos"
                scanButton.isEnabled = true
                checkAndInitializeBluetooth()
                Toast.makeText(this, "Permisos concedidos ✓", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Todos los permisos concedidos, BLE listo")
            } else {
                statusText.text = "❌ Permisos denegados"
                AlertDialog.Builder(this)
                    .setTitle("Permisos requeridos")
                    .setMessage("Los permisos son necesarios para escanear dispositivos BLE. ¿Quieres configurarlos manualmente?")
                    .setPositiveButton("Abrir Configuración") { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        Toast.makeText(this, "La app no funcionará sin permisos", Toast.LENGTH_LONG).show()
                    }
                    .show()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                statusText.text = "✅ Bluetooth activado"
                checkAndInitializeBluetooth()
                Log.d(TAG, "Bluetooth activado por usuario")
            } else {
                statusText.text = "❌ Bluetooth requerido"
                Toast.makeText(this, "Se necesita Bluetooth para escanear dispositivos", Toast.LENGTH_LONG).show()
                Log.w(TAG, "Usuario rechazó activar Bluetooth")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar estado cada vez que la app se reanuda
        checkAndInitializeBluetooth()
    }

    override fun onDestroy() {
        super.onDestroy()
        // stopSensorReadings() // No es necesario
        bluetoothGatt?.close()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "App destruida")
    }
}