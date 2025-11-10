package com.example.roommonitorapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class PermissionHelper(private val activity: AppCompatActivity) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val BLUETOOTH_REQUEST_CODE = 101
        const val TAG = "PermissionHelper"
    }

    fun hasAllRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun requestAllPermissions() {
        val permissions = getRequiredPermissions()
        Log.d(TAG, "Solicitando permisos: ${permissions.joinToString()}")

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(activity)
                .setTitle("Permisos necesarios para BLE")
                .setMessage("Para escanear y conectar dispositivos Bluetooth necesitamos:\n\n• Ubicación (para encontrar dispositivos)\n• Permisos de Bluetooth\n\nEstos permisos son esenciales para la funcionalidad BLE.")
                .setPositiveButton("Entendido") { _, _ ->
                    ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    Toast.makeText(activity, "Permisos necesarios", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    fun handlePermissionResult(grantResults: IntArray): Boolean {
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            Toast.makeText(activity, "Permisos concedidos ✓", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Todos los permisos concedidos")
            return true
        } else {
            AlertDialog.Builder(activity)
                .setTitle("Permisos requeridos")
                .setMessage("Los permisos son necesarios para escanear dispositivos BLE. ¿Quieres configurarlos manualmente?")
                .setPositiveButton("Abrir Configuración") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    Toast.makeText(activity, "La app no funcionará sin permisos", Toast.LENGTH_LONG).show()
                }
                .show()
            return false
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}