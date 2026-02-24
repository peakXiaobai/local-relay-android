package com.peak.localrelay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RelayController {
    private const val MAX_LOG_LINES = 200

    private val _state = MutableStateFlow(RelayState())
    val state: StateFlow<RelayState> = _state.asStateFlow()

    fun setConfig(config: RelayConfig) {
        _state.value = _state.value.copy(
            targetBaseUrl = config.targetBaseUrl,
            localPort = config.localPort,
            bindAllInterfaces = config.bindAllInterfaces,
        )
    }

    fun setStatus(status: RelayStatus, error: String? = null) {
        _state.value = _state.value.copy(status = status, lastError = error)
    }

    fun appendLog(message: String) {
        val current = _state.value.logs
        val next = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + message
        } else {
            current + message
        }
        _state.value = _state.value.copy(logs = next)
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }
}
