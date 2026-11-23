package com.goorder.data.remote

import android.util.Log
import com.goorder.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MqttManager {
    private var mqttClient: MqttClient? = null
    private val topicToSubscribe = "pos/order/print"
    private val topicTestPrint = "pos/printer/test"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(
        username: String? = null,
        password: String? = null,
        onPrintOrderReceived: (String) -> Unit,
        onTestPrintReceived: ((String) -> Unit)? = null
    ) {
        if (mqttClient?.isConnected == true) return

        scope.launch {
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

                    val finalUsername = if (!username.isNullOrBlank()) username else BuildConfig.MQTT_USERNAME
                    val finalPassword = if (!password.isNullOrBlank()) password else BuildConfig.MQTT_PASSWORD

                    if (finalUsername.isNotBlank()) {
                        userName = finalUsername
                    }
                    if (finalPassword.isNotBlank()) {
                        this.password = finalPassword.toCharArray()
                    }
                }

                mqttClient?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d("MqttManager", "Berjaya connect ke MQTT Broker ($serverURI)! Subscribing ke: $topicToSubscribe & $topicTestPrint")
                        try {
                            mqttClient?.subscribe(topicToSubscribe, 1)
                            mqttClient?.subscribe(topicTestPrint, 1)
                            Log.d("MqttManager", "Berjaya melanggan topik: $topicToSubscribe & $topicTestPrint")
                        } catch (e: Exception) {
                            Log.e("MqttManager", "Gagal subscribe ke topik MQTT", e)
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w("MqttManager", "Sambungan MQTT terputus: ${cause?.message}")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.payload?.let { String(it) } ?: ""
                        Log.d("MqttManager", "Mesej MQTT diterima pada topik [$topic]:\n$payload")

                        when (topic) {
                            topicToSubscribe -> {
                                if (payload.isNotBlank()) {
                                    onPrintOrderReceived(payload)
                                }
                            }
                            topicTestPrint -> {
                                onTestPrintReceived?.invoke(payload)
                            }
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                mqttClient?.connect(options)
            } catch (e: Exception) {
                Log.e("MqttManager", "Ralat menyambung ke MQTT Broker: ${e.message}", e)
            }
        }
    }

    /**
     * Menghantar senarai cart_id yang telah dicetak ke broker MQTT pada topik pos/order/printed
     * supaya backend Go mengemas kini lajur printed = true dalam database server.
     */
    fun publishPrintedCartIds(cartIds: List<String>) {
        val validUuids = cartIds.filter { id ->
            try {
                UUID.fromString(id)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (validUuids.isEmpty()) return

        scope.launch {
            try {
                val client = mqttClient
                if (client != null && client.isConnected) {
                    val jsonArray = JSONArray()
                    validUuids.forEach { jsonArray.put(it) }
                    val jsonObject = JSONObject().apply {
                        put("cart_ids", jsonArray)
                    }
                    val payload = jsonObject.toString().toByteArray(Charsets.UTF_8)
                    val message = MqttMessage(payload).apply {
                        qos = 1
                    }
                    client.publish("pos/order/printed", message)
                    Log.d("MqttManager", "✅ Berjaya publish ${validUuids.size} cart_id ke [pos/order/printed]: $jsonObject")
                } else {
                    Log.w("MqttManager", "⚠️ Tidak dapat publish ke [pos/order/printed]: MQTT client belum bersambung")
                }
            } catch (e: Exception) {
                Log.e("MqttManager", "❌ Ralat publish ke [pos/order/printed]: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                if (mqttClient?.isConnected == true) {
                    mqttClient?.disconnect()
                }
                mqttClient = null
            } catch (e: Exception) {
                Log.e("MqttManager", "Ralat mematikan MQTT: ${e.message}", e)
            }
        }
    }
}
