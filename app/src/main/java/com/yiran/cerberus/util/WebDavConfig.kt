package com.yiran.cerberus.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WebDavConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = true,
    val autoSync: Boolean = false
) {
    fun isValid(): Boolean {
        return enabled && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }
    
    fun getBackupFolder(): String {
        return "cerberus"
    }
    
    fun generateBackupFileName(): String {
        val formatter = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return "backup_${formatter.format(java.util.Date())}.cerb"
    }

    fun toJson(): String {
        return Json.encodeToString(this)
    }

    companion object {
        fun fromJson(json: String): WebDavConfig {
            return try {
                Json.decodeFromString(json)
            } catch (_: Exception) {
                WebDavConfig()
            }
        }
    }
}