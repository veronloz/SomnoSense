package com.example.roommonitorapp

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BluetoothManager(private val context: Context) {

    // Callbacks para la Activity
    interface BluetoothListener {
        fun onDevicesFound(devices: List<BluetoothDevice>)
        fun onConnectionStateChanged(connected: Boolean, message: String)
        fun onSensorDataUpdated(temperature: Float, humidity: Float, gasLevel: Int)
        fun onError(message: String)
    }

    private var listener: BluetoothListener? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isConnected = false

    private val handler = Handler(Looper.getMainLooper())

    // UUIDs para sensores - definidos como propiedades normales
    private val ENVIRONMENTAL_SENSING_SERVICE = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
    private val TEMPERATURE_CHARACTERISTIC = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
    private val HUMIDITY_CHARACTERISTIC = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")
    private val GAS_SENSOR_SERVICE = UUID.fromString("0000181c-0000-1000-8000-00805f9b34fb")
    private val GAS_CHARACTERISTIC = UUID.fromString("00002b00-0000-1000-8000-00805f9b34fb")

    private val foundDevices = mutableListOf<BluetoothDevice>()

    // Companion object para constantes
    companion object {
        const val TAG = "BluetoothManager"
        const val SCAN_PERIOD: Long = 10000
        const val READING_INTERVAL: Long = 2000
    }

    init {
        // Usar un nombre diferente para evitar conflicto con el nombre de la clase
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothAdapter = btManager?.adapter  // Ahora sí encuentra 'adapter'
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun setListener(listener: BluetoothListener) {
        this.listener = listener
    }

    // Escaneo BLE
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return

        isScanning = true
        foundDevices.clear()

        bleScanner?.startScan(scanCallback)

        handler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)

        Log.d(TAG, "Escaneo BLE iniciado")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return

        bleScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Escaneo BLE detenido")
    }

    // Conexión BLE
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        listener?.onConnectionStateChanged(false, "Conectando...")

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d(TAG, "Conectando a: ${device.address}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        stopSensorReadings()
        listener?.onConnectionStateChanged(false, "Desconectado")
        Log.d(TAG, "Dispositivo desconectado")
    }

    // Lecturas de sensores
    @SuppressLint("MissingPermission")
    private fun setupSensorReadings(gatt: BluetoothGatt) {
        var sensorsFound = 0

        // Temperatura
        val tempService = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE)
        val tempChar = tempService?.getCharacteristic(TEMPERATURE_CHARACTERISTIC)
        if (tempChar != null) {
            gatt.setCharacteristicNotification(tempChar, true)
            gatt.readCharacteristic(tempChar)
            sensorsFound++
        }

        // Humedad
        val humidityChar = tempService?.getCharacteristic(HUMIDITY_CHARACTERISTIC)
        if (humidityChar != null) {
            gatt.setCharacteristicNotification(humidityChar, true)
            gatt.readCharacteristic(humidityChar)
            sensorsFound++
        }

        // Gas
        val gasService = gatt.getService(GAS_SENSOR_SERVICE)
        val gasChar = gasService?.getCharacteristic(GAS_CHARACTERISTIC)
        if (gasChar != null) {
            gatt.setCharacteristicNotification(gasChar, true)
            gatt.readCharacteristic(gasChar)
            sensorsFound++
        }

        if (sensorsFound > 0) {
            startContinuousReadings()
            listener?.onConnectionStateChanged(true, "$sensorsFound sensores configurados")
        } else {
            listener?.onError("No se encontraron sensores")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startContinuousReadings() {
        handler.post(object : Runnable {
            override fun run() {
                if (isConnected && bluetoothGatt != null) {
                    bluetoothGatt?.let { gatt ->
                        val tempService = gatt.getService(ENVIRONMENTAL_SENSING_SERVICE)
                        tempService?.getCharacteristic(TEMPERATURE_CHARACTERISTIC)?.let {
                            gatt.readCharacteristic(it)
                        }
                        tempService?.getCharacteristic(HUMIDITY_CHARACTERISTIC)?.let {
                            gatt.readCharacteristic(it)
                        }
                        val gasService = gatt.getService(GAS_SENSOR_SERVICE)
                        gasService?.getCharacteristic(GAS_CHARACTERISTIC)?.let {
                            gatt.readCharacteristic(it)
                        }
                    }
                    handler.postDelayed(this, READING_INTERVAL)
                }
            }
        })
    }

    private fun stopSensorReadings() {
        handler.removeCallbacksAndMessages(null)
    }

    // Procesamiento de datos
    private fun processSensorData(characteristic: BluetoothGattCharacteristic) {
        val temperature: Float
        val humidity: Float
        val gasLevel: Int

        when (characteristic.uuid) {
            TEMPERATURE_CHARACTERISTIC -> {
                temperature = parseTemperatureData(characteristic.value)
                humidity = 0.0f
                gasLevel = 0
                listener?.onSensorDataUpdated(temperature, humidity, gasLevel)
            }
            HUMIDITY_CHARACTERISTIC -> {
                temperature = 0.0f
                humidity = parseHumidityData(characteristic.value)
                gasLevel = 0
                listener?.onSensorDataUpdated(temperature, humidity, gasLevel)
            }
            GAS_CHARACTERISTIC -> {
                temperature = 0.0f
                humidity = 0.0f
                gasLevel = parseGasData(characteristic.value)
                listener?.onSensorDataUpdated(temperature, humidity, gasLevel)
            }
            else -> return
        }
    }

    private fun parseTemperatureData(data: ByteArray?): Float {
        if (data == null || data.size < 2) return 0.0f
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.short.toFloat() / 100.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando temperatura: ${e.message}")
            0.0f
        }
    }

    private fun parseHumidityData(data: ByteArray?): Float {
        if (data == null || data.size < 2) return 0.0f
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            (buffer.short.toInt() and 0xFFFF).toFloat() / 100.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando humedad: ${e.message}")
            0.0f
        }
    }

    private fun parseGasData(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return 0
        return try {
            when (data.size) {
                1 -> data[0].toInt() and 0xFF
                2 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                4 -> ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando gas: ${e.message}")
            0
        }
    }

    // Callbacks
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!foundDevices.any { it.address == device.address }) {
                foundDevices.add(device)
                listener?.onDevicesFound(foundDevices.toList())
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onError("Error en escaneo: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    gatt.discoverServices()
                    listener?.onConnectionStateChanged(true, "Conectado - Descubriendo servicios...")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    stopSensorReadings()
                    listener?.onConnectionStateChanged(false, "Desconectado")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupSensorReadings(gatt)
            } else {
                listener?.onError("Error descubriendo servicios: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processSensorData(characteristic)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            processSensorData(characteristic)
        }
    }

    fun isScanning(): Boolean = isScanning
    fun isConnected(): Boolean = isConnected

    fun cleanup() {
        stopScan()
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}