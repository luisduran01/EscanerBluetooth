package com.example.escanerbluetooth

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.escanerbluetooth.ui.theme.EscanerBluetoothTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscanerBluetoothTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PantallaEscaneoBluetooth(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PantallaEscaneoBluetooth(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scanManager = remember { BluetoothScanManager(context) }
    
    val permisosNecesarios = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    val lanzadorPermisos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        if (resultado.values.all { it }) {
            scanManager.iniciarEscaneo()
        }
    }
    
    LaunchedEffect(scanManager.escaneando) {
        if (scanManager.escaneando) {
            delay(10_000)
            scanManager.detenerEscaneo()
        }
    }
    
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { lanzadorPermisos.launch(permisosNecesarios) },
            enabled = !scanManager.escaneando
        ) {
            Text(if (scanManager.escaneando) "Escaneando..." else "Escanear")
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "${scanManager.dispositivos.size} dispositivo(s) encontrado(s)",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Text(
            text = "Estado: ${scanManager.estadoConexion}",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn {
            items(scanManager.dispositivos) { dispositivo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { scanManager.conectar(dispositivo) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dispositivo.nombre, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (dispositivo.tipo == TipoBluetooth.BLE) "BLE" else "Clásico",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(dispositivo.direccion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}