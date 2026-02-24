package com.peak.localrelay

data class RelayConfig(
    val targetBaseUrl: String,
    val localPort: Int,
    val bindAllInterfaces: Boolean,
)

enum class RelayStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class RelayState(
    val status: RelayStatus = RelayStatus.STOPPED,
    val targetBaseUrl: String = "http://118.196.100.121:3005",
    val localPort: Int = 3005,
    val bindAllInterfaces: Boolean = false,
    val lastError: String? = null,
    val logs: List<String> = emptyList(),
)
