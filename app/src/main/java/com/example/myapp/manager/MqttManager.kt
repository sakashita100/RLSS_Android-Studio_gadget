package com.example.bluetoothmqtt.mqtt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.bluetoothmqtt.bluetooth.BluetoothManager
import org.eclipse.paho.client.mqttv3.*
import java.util.*

class MQTTManager(
    private val brokerUrl: String,
    private val topic: String,
    private val bluetoothManager: BluetoothManager // 追加
) {
    private var client: MqttClient? = null

    fun connect() {
        try {
            client = MqttClient(brokerUrl, MqttClient.generateClientId(), null)
            val options = MqttConnectOptions()
            options.isCleanSession = true

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d("MQTT", "接続喪失: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString()
                    Log.d("MQTT", "受信: $payload")

                    // --- 🔽 ここでBluetooth接続トリガー処理 ---
                    if (payload != null && payload.startsWith("connect:")) {
                        val address = payload.removePrefix("connect:").trim()
                        val device = getPairedDeviceByAddress(address)
                        if (device != null) {
                            bluetoothManager.connectToDevice(device)
                            bluetoothManager.startListening {
                                Log.d("Bluetooth", "受信データ: $it")
                            }
                        } else {
                            Log.e("Bluetooth", "指定アドレスのデバイスが見つかりません: $address")
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)
            client?.subscribe(topic)
            Log.d("MQTT", "接続 & 購読完了: $topic")
        } catch (e: MqttException) {
            Log.e("MQTT", "接続エラー: ${e.message}")
        }
    }

    private fun getPairedDeviceByAddress(address: String): BluetoothDevice? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.bondedDevices?.find { it.address == address }
    }
}
