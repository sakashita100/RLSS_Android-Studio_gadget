package com.example.bluetoothmqtt.mqtt

import org.eclipse.paho.client.mqttv3.*
import java.util.*

class MqttManager(brokerUrl: String, clientId: String) {

    private val mqttClient: MqttClient = MqttClient(brokerUrl, "$clientId-${UUID.randomUUID()}", null)

    init {
        val options = MqttConnectOptions().apply {
            isCleanSession = true
        }

        try {
            mqttClient.connect(options)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, payload: String) {
        try {
            val message = MqttMessage(payload.toByteArray())
            message.qos = 1
            mqttClient.publish(topic, message)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}