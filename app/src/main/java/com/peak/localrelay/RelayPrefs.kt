package com.peak.localrelay

import android.content.Context

object RelayPrefs {
    private const val PREFS = "relay_prefs"
    private const val KEY_TARGET = "target_url"
    private const val KEY_PORT = "local_port"
    private const val KEY_BIND_ALL = "bind_all"

    fun load(context: Context): RelayConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = prefs.getString(KEY_TARGET, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, 3005)
        val bindAll = prefs.getBoolean(KEY_BIND_ALL, false)

        return RelayConfig(
            targetBaseUrl = if (target.isBlank()) "http://118.196.100.121:3005" else target,
            localPort = if (port in 1..65535) port else 3005,
            bindAllInterfaces = bindAll,
        )
    }

    fun save(context: Context, config: RelayConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET, config.targetBaseUrl)
            .putInt(KEY_PORT, config.localPort)
            .putBoolean(KEY_BIND_ALL, config.bindAllInterfaces)
            .apply()
    }
}
