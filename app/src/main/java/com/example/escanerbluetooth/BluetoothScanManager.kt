package com.example.escanerbluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

enum class TipoBluetooth { BLE, CLASICO }

// Un dispositivo Bluetooth encontrado en el escaneo (BLE o clasico)
data class DispositivoBt(
    val nombre: String,
    val direccion: String,
    val tipo: TipoBluetooth,
    val device: BluetoothDevice
)

// Escanea y conecta por BLE (ESP32, wearables...) y por Bluetooth clasico
// (mouse, joystick, audio, modulos SPP como HC-05). Expone su estado a Compose.
class BluetoothScanManager(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var leScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var socketClasico: BluetoothSocket? = null
    private var nombreConectando: String = ""
    private var receptorRegistrado = false

    val dispositivos = mutableStateListOf<DispositivoBt>()
    
    var escaneando by mutableStateOf(false)
        private set
        
    var estadoConexion by mutableStateOf("Desconectado")
        private set

    val bluetoothDisponible: Boolean
        get() = adapter != null && adapter.isEnabled

    private val receptorClasico = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let { agregarSiNuevo(it, TipoBluetooth.CLASICO) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun iniciarEscaneo() {
        if (!bluetoothDisponible) {
            estadoConexion = "Activa el Bluetooth del telefono"
            return
        }
        dispositivos.clear()
        escaneando = true
        
        // BLE: ESP32, wearables, beacons...
        leScanner = adapter?.bluetoothLeScanner
        leScanner?.startScan(scanCallbackBle)
        
        // Clasico: mouse, joystick, audio, modulos SPP...
        ContextCompat.registerReceiver(
            context, receptorClasico, IntentFilter(BluetoothDevice.ACTION_FOUND),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receptorRegistrado = true
        adapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun detenerEscaneo() {
        if (!escaneando) return
        leScanner?.stopScan(scanCallbackBle)
        adapter?.cancelDiscovery()
        if (receptorRegistrado) {
            runCatching { context.unregisterReceiver(receptorClasico) }
            receptorRegistrado = false
        }
        escaneando = false
    }

    @SuppressLint("MissingPermission")
    private fun agregarSiNuevo(device: BluetoothDevice, tipo: TipoBluetooth) {
        val yaExiste = dispositivos.any { it.direccion == device.address }
        if (!yaExiste) {
            dispositivos.add(
                DispositivoBt(
                    nombre = device.name ?: "Dispositivo sin nombre",
                    direccion = device.address,
                    tipo = tipo,
                    device = device
                )
            )
        }
    }

    private val scanCallbackBle = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            agregarSiNuevo(result.device, TipoBluetooth.BLE)
        }
        override fun onScanFailed(errorCode: Int) {
            estadoConexion = "Fallo el escaneo BLE (codigo $errorCode)"
        }
    }

    @SuppressLint("MissingPermission")
    fun conectar(dispositivo: DispositivoBt) {
        detenerEscaneo()
        nombreConectando = dispositivo.nombre
        estadoConexion = "Conectando a ${dispositivo.nombre}..."
        
        when (dispositivo.tipo) {
            TipoBluetooth.BLE -> {
                gatt = dispositivo.device.connectGatt(
                    context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
            }
            TipoBluetooth.CLASICO -> conectarClasico(dispositivo.device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun conectarClasico(device: BluetoothDevice) {
        // SPP funciona con dispositivos que exponen puerto serie (ESP32 en modo
        // clasico, HC-05/HC-06). Con un mouse o joystick esto normalmente FALLA
        // -- hablan el perfil HID, que Android maneja a nivel de sistema, no a
        // traves de un socket que la app pueda leer -- asi que si el SPP falla,
        // se cae a emparejar (createBond) en vez de forzar una conexion.
        Thread {
            try {
                val uuidSpp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                adapter?.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(uuidSpp)
                socket.connect()
                socketClasico = socket
                estadoConexion = "Conectado a ${device.name} (clasico, SPP)"
            } catch (e: IOException) {
                estadoConexion = "${device.name}: sin SPP, iniciando emparejamiento del sistema..."
                device.createBond()
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun desconectar() {
        gatt?.disconnect(); gatt?.close(); gatt = null
        runCatching { socketClasico?.close() }
        socketClasico = null
        estadoConexion = "Desconectado"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> estadoConexion = "Conectado a $nombreConectando (BLE)"
                BluetoothProfile.STATE_DISCONNECTED -> estadoConexion = "Desconectado"
            }
        }
    }
}
