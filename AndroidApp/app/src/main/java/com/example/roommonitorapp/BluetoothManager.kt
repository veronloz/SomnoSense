package com.example.roommonitorapp

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BluetoothManager(private val context: Context) {

    interface BluetoothListener {
        fun onDevicesFound(devices: List<BluetoothDevice>)
        fun onConnectionStateChanged(connected: Boolean, message: String)
        fun onSensorDataUpdated(temperature: Float, humidity: Float, gasLevel: Int)
        fun onError(message: String)
    }

    private var listener: BluetoothListener? = null

    private val btAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE)
                as android.bluetooth.BluetoothManager).adapter

    private val bleScanner = btAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableSetOf<BluetoothDevice>()

    private var isConnected = false
    private var isScanning = false

    // UUIDs
    private val SERVICE_UUID =
        UUID.fromString("47617353-656e-736f-7253-766300000000")

    private val DATA_CHAR_UUID =
        UUID.fromString("47617352-6561-6469-6e67-73000000000")

    private val CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun setListener(listener: BluetoothListener) {
        this.listener = listener
    }

    // 🔍 SCAN
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning) return
        foundDevices.clear()
        isScanning = true
        bleScanner.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    // 🔗 CONNECT
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback)
        listener?.onConnectionStateChanged(false, "Conectando...")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        listener?.onConnectionStateChanged(false, "Desconectado")
    }

    // 🔁 SCAN CALLBACK
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            if (foundDevices.add(result.device)) {
                listener?.onDevicesFound(foundDevices.toList())
            }
        }
    }

    // 🔧 GATT CALLBACK
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                gatt.discoverServices()
            } else {
                isConnected = false
                listener?.onConnectionStateChanged(false, "Desconectado")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(DATA_CHAR_UUID)

            if (characteristic == null) {
                listener?.onError("Characteristic no encontrada")
                return
            }

            gatt.setCharacteristicNotification(characteristic, true)

            val cccd = characteristic.getDescriptor(CCCD_UUID)
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)

            listener?.onConnectionStateChanged(true, "🟢 Conectado al nRF52")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            parseGasPacket(characteristic.value)
        }
    }

    // 📦 Parsear EXACTAMENTE los 5 floats del firmware
    private fun parseGasPacket(data: ByteArray?) {
        if (data == null || data.size < 20) return

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val co = buffer.float
        val no2 = buffer.float
        val nh3 = buffer.float
        val ch4 = buffer.float
        val etoh = buffer.float

        // En tu UI solo muestras 3 → elegimos los principales
        listener?.onSensorDataUpdated(
            temperature = co,
            humidity = no2,
            gasLevel = etoh.toInt()
        )
    }

    fun isScanning() = isScanning
    fun isConnected() = isConnected

    fun cleanup() {
        stopScan()
        disconnect()
    }
}
