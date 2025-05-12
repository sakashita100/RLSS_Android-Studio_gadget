package com.example.myapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetoothmqtt.bluetooth.BluetoothManager
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val bluetoothManager = BluetoothManager()
    private val mqttClient: MqttClient =
        MqttClient("tcp://broker.emqx.io:1883", "android-client", null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            mqttClient.connect()
            Log.d("MQTT", "Connected to broker")
        } catch (e: Exception) {
            Log.e("MQTT", "MQTT connect failed: ${e.message}")
        }

        setContent {
            val context = LocalContext.current
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }
            var sensorData by remember { mutableStateOf("データ受信待ち...") }
            var pairedDevices by remember { mutableStateOf(setOf<BluetoothDevice>()) }
            var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
            var isDeviceConnected by remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                onDispose {
                    bluetoothManager.stopListening()
                    if (mqttClient.isConnected) mqttClient.disconnect()
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("Bluetooth & MQTT アプリ") })
                },
                content = { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bluetooth ON/OFF
                        Row {
                            Text("Bluetooth: ${if (isBluetoothEnabled) "オン" else "オフ"}")
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                if (bluetoothAdapter != null) {
                                    if (!bluetoothAdapter.isEnabled) {
                                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        context.startActivity(intent)
                                    } else {
                                        bluetoothAdapter.disable()
                                    }
                                    isBluetoothEnabled = bluetoothAdapter.isEnabled
                                }
                            }) {
                                Text(if (isBluetoothEnabled) "Bluetoothをオフにする" else "Bluetoothをオンにする")
                            }
                        }

                        Divider()

                        // ペアリング済みデバイス一覧表示
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                            } else {
                                ActivityCompat.requestPermissions(
                                    this@MainActivity,
                                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                                    1
                                )
                            }
                        }) {
                            Text("ペアリング済みデバイスを表示")
                        }

                        if (pairedDevices.isNotEmpty()) {
                            Text("デバイスを選択してください：")
                            pairedDevices.forEach { device ->
                                Text(
                                    text = "• ${device.name ?: "不明なデバイス"}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedDevice = device
                                        }
                                        .padding(4.dp)
                                )
                            }

                            selectedDevice?.let { device ->
                                Button(onClick = {
                                    Toast.makeText(
                                        context,
                                        "${device.name} に接続中...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    try {
                                        bluetoothManager.stopListening()
                                        bluetoothManager.connectToDevice(device)
                                        bluetoothManager.startListening { data ->
                                            sensorData = data
                                        }
                                        isDeviceConnected = true
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "接続失敗: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        isDeviceConnected = false
                                    }
                                }) {
                                    Text("このデバイスに接続")
                                }
                            }
                        } else {
                            Text("ペアリング済みデバイスがありません。")
                        }

                        Divider()

                        // データ表示
                        Text("受信データ:")
                        Text(sensorData)

                        Divider()

                        // MQTT送信ボタン（全データ）
                        Button(
                            onClick = {
                                mqttClient.publish("m5stack/sensor", sensorData)
                                Toast.makeText(context, "MQTTに送信しました", Toast.LENGTH_SHORT).show()
                            },
                            enabled = isDeviceConnected && sensorData != "データ受信待ち..."
                        ) {
                            Text("MQTTで全データを送信")
                        }

                        // MQTT送信ボタン（気圧のみ）
                        Button(
                            onClick = {
                                try {
                                    val json = JSONObject(sensorData)
                                    val pressure = json.optString("pressure", "不明")
                                    mqttClient.publish("m5stack/sensor", pressure)
                                    Toast.makeText(
                                        context,
                                        "気圧データを送信しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    mqttClient.publish("m5stack/sensor", "parse error")
                                    Toast.makeText(
                                        context,
                                        "気圧送信に失敗しました",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = isDeviceConnected && sensorData != "データ受信待ち..."
                        ) {
                            Text("気圧データのみを送信")
                        }
                    }
                }
            )
        }
    }

    // パーミッション結果処理
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth権限が許可されました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth権限が拒否されました", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// 拡張関数でMQTTパブリッシュ
private fun MqttClient.publish(topic: String, payload: String) {
    try {
        val message = MqttMessage()
        message.payload = payload.toByteArray()
        message.qos = 0
        this.publish(topic, message)
        Log.d("MQTT", "Published to $topic: $payload")
    } catch (e: Exception) {
        Log.e("MQTT", "Publish error: ${e.message}")
        e.printStackTrace()
    }
}
