package top.deepcommunity.app.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import top.deepcommunity.app.core.common.Constants
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentCookieJar @Inject constructor(
    @ApplicationContext private val context: Context
) : CookieJar {

    private val prefs = context.getSharedPreferences(Constants.COOKIE_PREFS_NAME, Context.MODE_PRIVATE)
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        loadCookiesFromPrefs()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val currentList = cookieStore.getOrPut(host) { mutableListOf() }
        
        for (newCookie in cookies) {
            currentList.removeAll { it.name == newCookie.name }
            // 过滤未过期 Cookie
            if (newCookie.expiresAt > System.currentTimeMillis()) {
                currentList.add(newCookie)
            }
        }
        persistCookiesToPrefs(host, currentList)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        
        // 过滤过期 Cookie
        val validCookies = cookies.filter { it.expiresAt > now }
        if (validCookies.size != cookies.size) {
            cookieStore[host] = validCookies.toMutableList()
            persistCookiesToPrefs(host, validCookies)
        }
        return validCookies
    }

    fun clearAllCookies() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }

    private fun persistCookiesToPrefs(host: String, cookies: List<Cookie>) {
        val encoded = cookies.joinToString("|||") { encodeCookie(it) }
        prefs.edit().putString(host, encoded).apply()
    }

    private fun loadCookiesFromPrefs() {
        for ((host, serialized) in prefs.all) {
            if (serialized is String && serialized.isNotEmpty()) {
                val list = serialized.split("|||").mapNotNull { decodeCookie(it) }
                cookieStore[host] = list.toMutableList()
            }
        }
    }

    private fun encodeCookie(cookie: Cookie): String {
        return buildString {
            append(cookie.name).append("=")
            append(cookie.value).append(";")
            append(cookie.expiresAt).append(";")
            append(cookie.domain).append(";")
            append(cookie.path).append(";")
            append(cookie.secure).append(";")
            append(cookie.httpOnly)
        }
    }

    private fun decodeCookie(serialized: String): Cookie? {
        val parts = serialized.split(";")
        if (parts.size < 6) return null
        return try {
            val nameValue = parts[0].split("=", limit = 2)
            val expiresAt = parts[1].toLong()
            val domain = parts[2]
            val path = parts[3]
            val secure = parts[4].toBoolean()
            val httpOnly = parts[5].toBoolean()

            var builder = Cookie.Builder()
                .name(nameValue[0])
                .value(nameValue[1])
                .expiresAt(expiresAt)
                .path(path)
            
            builder = if (domain.startsWith(".")) {
                builder.domain(domain.removePrefix("."))
            } else {
                builder.hostOnlyDomain(domain)
            }
            if (secure) builder = builder.secure()
            if (httpOnly) builder = builder.httpOnly()

            builder.build()
        } catch (e: Exception) {
            null
        }
    }
}
