package com.tiktok.webmod.data

enum class ProtocolType {
    VLESS,
    TROJAN,
    SHADOWSOCKS,
    SOCKS5,
    HTTP,
    DIRECT
}

data class ProxyNode(
    val protocol: ProtocolType,
    val host: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val method: String? = null,
    val sni: String? = null,
    val security: String? = null,
    val path: String? = null,
    val remark: String = "",
    val rawUri: String = ""
) {
    fun getSummary(): String {
        return "$protocol - $host:$port ${if (remark.isNotEmpty()) "($remark)" else ""}"
    }
}
