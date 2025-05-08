package com.example.bluetoothmqtt.mqtt

import android.content.ContentValues.TAG
import org.eclipse.paho.client.mqttv3.*
import android.content.Context
import android.util.Log
import org.eclipse.paho.android.service.MqttAndroidClient
import java.util.*




class MqttManager(context: Context, brokerUrl: String, clientId: String) {

    private val mqttClient: MqttAndroidClient =
        MqttAndroidClient(context.applicationContext, brokerUrl, clientId)

    fun connect(onConnected: (() -> Unit)? = null, onMessageReceived: ((String, String) -> Unit)? = null) {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.d("MQTT", "Connection lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString() ?: return
                Log.d("MQTT", "Message arrived: $payload")
                if (topic != null) {
                    onMessageReceived?.invoke(topic, payload)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d("MQTT", "Delivery complete")
            }
        })

        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Connected to broker")
                onConnected?.invoke()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Connection failed: ${exception?.message}")
            }
        })
    }

    fun subscribe(topic: String, qos: Int = 0) {
        mqttClient.subscribe(topic, qos, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Subscribed to $topic")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Subscribe failed: ${exception?.message}")
            }
        })
    }

    fun publish(topic: String, payload: String) {
        try {
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            mqttClient.publish(topic, message)
            Log.d("MQTT", "Message published to $topic")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            mqttClient.disconnect()
            Log.d("MQTT", "Disconnected from broker")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}
