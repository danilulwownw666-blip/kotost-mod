package com.tiktok.webmod.data

import android.content.Context
import android.content.SharedPreferences

class ConfigPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tiktok_mod_prefs", Context.MODE_PRIVATE)

    var proxyUri: String
        get() = prefs.getString(KEY_PROXY_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROXY_URI, value).apply()

    var selectedRegion: String
        get() = prefs.getString(KEY_REGION, "US") ?: "US"
        set(value) = prefs.edit().putString(KEY_REGION, value).apply()

    var isProxyActive: Boolean
        get() = prefs.getBoolean(KEY_PROXY_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_PROXY_ACTIVE, value).apply()

    var localPort: Int
        get() = prefs.getInt(KEY_LOCAL_PORT, 10808)
        set(value) = prefs.edit().putInt(KEY_LOCAL_PORT, value).apply()

    companion object {
        private const val KEY_PROXY_URI = "key_proxy_uri"
        private const val KEY_REGION = "key_region"
        private const val KEY_PROXY_ACTIVE = "key_proxy_active"
        private const val KEY_LOCAL_PORT = "key_local_port"
    }
}
