package com.example.bluetoothmqtt.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BluetoothManager {
    private val socketMap = ConcurrentHashMap<String, BluetoothSocket>()
    private val outputStreamMap = ConcurrentHashMap<String, OutputStream>()
    private val inputStreamMap = ConcurrentHashMap<String, InputStream>()
    private var listener: ((String) -> Unit)? = null

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    //val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    //val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

    fun connectToDevice(device: BluetoothDevice) {
        // 既に接続されていれば一旦切断
        if (socketMap.containsKey(device.address)) {
            Log.d("BluetoothManager", "再接続のため既存接続を切断: ${device.name}")
            disconnectFromDevice(device)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                socketMap[device.address] = socket
                outputStreamMap[device.address] = socket.outputStream
                inputStreamMap[device.address] = socket.inputStream

                Log.d("BluetoothManager", "接続成功: ${device.name}")
                startReading(device.address)
            } catch (e: Exception) {
                Log.e("BluetoothManager", "接続失敗: ${e.message}")
            }
        }
    }

    fun disconnectFromDevice(device: BluetoothDevice) {
        try {
            socketMap[device.address]?.close()
            socketMap.remove(device.address)
            inputStreamMap.remove(device.address)
            outputStreamMap.remove(device.address)
            Log.d("BluetoothManager", "切断成功: ${device.name}")
        } catch (e: IOException) {
            Log.e("BluetoothManager", "切断失敗: ${e.message}")
        }
    }

    fun disconnectAll() {
        socketMap.values.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e("BluetoothManager", "切断エラー: ${e.message}")
            }
        }
        socketMap.clear()
        outputStreamMap.clear()
        inputStreamMap.clear()
    }

    private fun startReading(address: String) {
        val input = inputStreamMap[address] ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            try {
                while (true) {
                    val bytes = input.read(buffer)
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        listener?.invoke(message)
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "読み取りエラー($address): ${e.message}")
            }
        }
    }

    fun sendMessageToAllDevices(message: String) {
        outputStreamMap.forEach { (address, outputStream) ->
            try {
                outputStream.write(message.toByteArray())
                Log.d("BluetoothManager", "送信成功($address): $message")
            } catch (e: Exception) {
                Log.e("BluetoothManager", "送信失敗($address): ${e.message}")
            }
        }
    }

    fun sendMessage(message: String) {
        socketMap.forEach { (address, socket) ->
            try {
                socket.outputStream.write(message.toByteArray())
                socket.outputStream.flush()
                Log.d("BluetoothManager", "送信成功($address): $message")
            } catch (e: Exception) {
                Log.e("BluetoothManager", "送信失敗($address): ${e.message}")
            }
        }
    }

    fun startListening(callback: (String) -> Unit) {
        listener = callback
    }

    fun isConnected(): Boolean = socketMap.isNotEmpty()
}
