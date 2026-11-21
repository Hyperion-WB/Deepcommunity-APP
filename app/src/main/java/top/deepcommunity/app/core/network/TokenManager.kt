package top.deepcommunity.app.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.deepcommunity.app.core.common.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                Constants.PREFS_SECURE_AUTH,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 兼容异常降级
            context.getSharedPreferences(Constants.PREFS_SECURE_AUTH, Context.MODE_PRIVATE)
        }
    }

    private val backupPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("deep_auth_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val _isLoggedIn = MutableStateFlow(!getAccessToken().isNullOrBlank())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun getAccessToken(): String? {
        val primary = try {
            prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        } catch (e: Exception) {
            null
        }
        return primary ?: backupPrefs.getString(Constants.KEY_ACCESS_TOKEN, null)
    }

    fun saveAccessToken(token: String) {
        try {
            prefs.edit().putString(Constants.KEY_ACCESS_TOKEN, token).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        backupPrefs.edit().putString(Constants.KEY_ACCESS_TOKEN, token).apply()
        _isLoggedIn.value = true
    }

    fun saveCurrentUserJson(userJson: String) {
        try {
            prefs.edit().putString(Constants.KEY_CURRENT_USER_JSON, userJson).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        backupPrefs.edit().putString(Constants.KEY_CURRENT_USER_JSON, userJson).apply()
    }

    fun getCurrentUserJson(): String? {
        val primary = try {
            prefs.getString(Constants.KEY_CURRENT_USER_JSON, null)
        } catch (e: Exception) {
            null
        }
        return primary ?: backupPrefs.getString(Constants.KEY_CURRENT_USER_JSON, null)
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        backupPrefs.edit().clear().apply()
        _isLoggedIn.value = false
    }
}
