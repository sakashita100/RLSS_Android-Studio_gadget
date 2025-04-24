package com.example.bluetoothmqtt.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.*

class BluetoothManager {
    private var bluetoothSocket: BluetoothSocket? = null
    private var listeningJob: Job? = null

    fun startListening(onDataReceived: (String) -> Unit) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = adapter.bondedDevices.firstOrNull {
            it.name.contains("M5") || it.name.contains("ESP")
        }

        device?.let {
            val uuid = it.uuids?.firstOrNull()?.uuid ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            bluetoothSocket = it.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()

            listeningJob = CoroutineScope(Dispatchers.IO).launch {
                val input: InputStream? = bluetoothSocket?.inputStream
                val buffer = ByteArray(1024)
                while (isActive && bluetoothSocket?.isConnected == true) {
                    try {
                        val bytes = input?.read(buffer)
                        bytes?.let {
                            val received = String(buffer, 0, it)
                            withContext(Dispatchers.Main) {
                                onDataReceived(received.trim())
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        bluetoothSocket?.close()
    }

    fun connectToDevice(device: BluetoothDevice) {

    }
}
