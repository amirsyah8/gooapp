package com.goorder.data.remote

import android.util.Log
import com.goorder.BuildConfig
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

class MqttManager {
    private var mqttClient: MqttClient? = null
    private val topicToSubscribe = "pos/order/print"

    fun connect(
        onPrintOrderReceived: (String) -> Unit
    ) {
        if (mqttClient?.isConnected == true) return

        try {
            val serverUri = BuildConfig.MQTT_BROKER
            val clientId = "android-printer-" + UUID.randomUUID().toString().take(8)

            Log.d("MqttManager", "Connecting to MQTT Broker: $serverUri with ClientID: $clientId")

            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
            }

            mqttClient?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d("MqttManager", "Berjaya connect ke MQTT Broker ($serverURI)! Subscribing ke: $topicToSubscribe")
                    try {
                        mqttClient?.subscribe(topicToSubscribe, 1)
                    } catch (e: Exception) {
                        Log.e("MqttManager", "Gagal subscribe ke $topicToSubscribe", e)
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w("MqttManager", "Sambungan MQTT terputus: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.payload?.let { String(it) } ?: ""
                    Log.d("MqttManager", "Mesej MQTT diterima pada topik [$topic]: $payload")
                    if (topic == topicToSubscribe && payload.isNotBlank()) {
                        onPrintOrderReceived(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient?.connect(options)
        } catch (e: Exception) {
            Log.e("MqttManager", "Ralat menyambung ke MQTT Broker", e)
        }
    }

    fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            mqttClient = null
        } catch (e: Exception) {
            Log.e("MqttManager", "Ralat mematikan MQTT", e)
        }
    }
}
