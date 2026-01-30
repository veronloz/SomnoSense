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
        fun onGasDataUpdated(co: Float, no2: Float, nh3: Float, ch4: Float, etoh: Float)
        fun onEnvDataUpdated(temp: Float, humidity: Float)
        fun onSoundDetected(count: Int)
        fun onError(message: String)
    }

    private var listener: BluetoothListener? = null
    private val btAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
    private val bleScanner: BluetoothLeScanner? get() = btAdapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private val foundDevices = mutableSetOf<BluetoothDevice>()

    private var isConnected = false
    private var isScanning = false

    // UUIDs EXACTOS
    private val SERVICE_UUID = UUID.fromString("47617353-656e-736f-7253-766300000000")
    private val GAS_CHAR_UUID = UUID.fromString("47617352-6561-6469-6e67-730000000000")
    private val ENV_CHAR_UUID = UUID.fromString("456e7669-726f-6e6d-656e-740000000000")
    private val SND_CHAR_UUID = UUID.fromString("536f756e-6444-6574-6563-740000000000")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun setListener(listener: BluetoothListener) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning || bleScanner == null) return
        foundDevices.clear()
        isScanning = true
        bleScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bleScanner?.stopScan(scanCallback)
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        Log.d("BLE_DEBUG", "Attempting connection to: ${device.address}")

        // 1. Force a complete stop of everything else
        stopScan()

        gatt?.let {
            Log.d("BLE_DEBUG", "Closing existing GATT before new connection")
            it.disconnect()
            it.close()
            gatt = null
        }

        listener?.onConnectionStateChanged(false, "Conectando...")

        handler.postDelayed({
            Log.d("BLE_DEBUG", "Connecting now...")
            gatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }, 1000)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        handler.post { listener?.onConnectionStateChanged(false, "Desconectado") }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            if (foundDevices.add(result.device)) {
                handler.post { listener?.onDevicesFound(foundDevices.toList()) }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE_DEBUG", "onConnectionStateChange: status=$status, newState=$newState")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                isConnected = false
                val errorMsg = "Error de conexión: $status"
                gatt.close()
                if (this@BluetoothManager.gatt == gatt) {
                    this@BluetoothManager.gatt = null
                }
                handler.post { listener?.onError(errorMsg) }
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                Log.d("BLE_DEBUG", "Connected, discovering services...")
                handler.postDelayed({ gatt.discoverServices() }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                handler.post { listener?.onConnectionStateChanged(false, "Desconectado") }
                gatt.close()
                this@BluetoothManager.gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BLE_DEBUG", "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // LOG ALL SERVICES FOUND
                gatt.services.forEach { s ->
                    Log.d("BLE_DEBUG", "Found Service: ${s.uuid}")
                }

                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d("BLE_DEBUG", "Target service found!")
                    enableNotification(gatt, service, GAS_CHAR_UUID)
                } else {
                    Log.e("BLE_DEBUG", "Target service NOT found: $SERVICE_UUID")
                    handler.post { listener?.onError("Servicio no encontrado") }
                }
            } else {
                handler.post { listener?.onError("Discovery failed: $status") }
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotification(gatt: BluetoothGatt, service: BluetoothGattService, charUuid: UUID) {
            val characteristic = service.getCharacteristic(charUuid)
            if (characteristic != null) {
                Log.d("BLE_DEBUG", "Enabling notifications for $charUuid")
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            } else {
                Log.e("BLE_DEBUG", "Characteristic not found: $charUuid")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val service = gatt.getService(SERVICE_UUID) ?: return
            when (descriptor.characteristic.uuid) {
                GAS_CHAR_UUID -> enableNotification(gatt, service, ENV_CHAR_UUID)
                ENV_CHAR_UUID -> enableNotification(gatt, service, SND_CHAR_UUID)
                SND_CHAR_UUID -> {
                    Log.d("BLE_DEBUG", "All notifications active.")
                    handler.post {
                        listener?.onConnectionStateChanged(true, "🟢 Conectado")
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                GAS_CHAR_UUID -> parseGasPacket(characteristic.value)
                ENV_CHAR_UUID -> parseEnvPacket(characteristic.value)
                SND_CHAR_UUID -> parseSoundPacket(characteristic.value)
            }
        }
    }

    private fun parseGasPacket(data: ByteArray?) {
        if (data == null || data.size < 20) return
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val co = buffer.float
        val no2 = buffer.float
        val nh3 = buffer.float
        val ch4 = buffer.float
        val etoh = buffer.float
        handler.post { listener?.onGasDataUpdated(co, no2, nh3, ch4, etoh) }
    }

    private fun parseEnvPacket(data: ByteArray?) {
        if (data == null || data.size < 8) return
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val temp = buffer.float
        val hum = buffer.float
        handler.post { listener?.onEnvDataUpdated(temp, hum) }
    }

    private fun parseSoundPacket(data: ByteArray?) {
        if (data == null || data.size < 4) return
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        handler.post { listener?.onSoundDetected(count) }
    }

    fun isConnected() = isConnected
    fun cleanup() { stopScan(); disconnect() }
}
