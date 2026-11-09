package com.goorder.data

import android.content.Context
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.util.date.GMTDate
import kotlinx.serialization.Serializable
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SerializableCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val extensions: Map<String, String?> = emptyMap()
)

fun Cookie.toSerializable() = SerializableCookie(
    name = name,
    value = value,
    domain = domain,
    path = path,
    expires = expires?.timestamp,
    secure = secure,
    httpOnly = httpOnly,
    extensions = extensions
)

fun SerializableCookie.toCookie() = Cookie(
    name = name,
    value = value,
    domain = domain,
    path = path,
    expires = expires?.let { GMTDate(it) },
    secure = secure,
    httpOnly = httpOnly,
    extensions = extensions
)

class PersistentCookieStorage(context: Context) : CookiesStorage {
    private val sharedPreferences = context.getSharedPreferences("ktor_cookies", Context.MODE_PRIVATE)
    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        loadFromStorage()
    }

    private fun loadFromStorage() {
        sharedPreferences.all.forEach { (host, value) ->
            if (value is String) {
                try {
                    val list = Json.decodeFromString<List<SerializableCookie>>(value)
                    cookies[host] = list.map { it.toCookie() }.toMutableList()
                } catch (_: Exception) {
                    // Ignore malformed data
                }
            }
        }
    }

    private fun saveToStorage(host: String) {
        val list = cookies[host] ?: return
        val json = Json.encodeToString(list.map { it.toSerializable() })
        sharedPreferences.edit { putString(host, json) }
    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val host = requestUrl.host
        val now = GMTDate()
        val hostCookies = cookies[host] ?: return emptyList()

        // Filter out expired cookies
        val validCookies = hostCookies.filter { it.expires == null || it.expires!! > now }
        
        if (validCookies.size != hostCookies.size) {
            cookies[host] = validCookies.toMutableList()
            saveToStorage(host)
        }
        
        return validCookies
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val host = requestUrl.host
        val hostCookies = cookies.getOrPut(host) { mutableListOf() }
        
        hostCookies.removeAll { it.name == cookie.name }
        hostCookies.add(cookie)
        
        saveToStorage(host)
    }

    fun clear() {
        cookies.clear()
        sharedPreferences.edit { clear() }
    }

    override fun close() {}
}
