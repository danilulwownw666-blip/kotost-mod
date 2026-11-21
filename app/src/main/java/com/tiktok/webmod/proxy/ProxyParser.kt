package com.tiktok.webmod.proxy

import android.net.Uri
import android.util.Base64
import com.tiktok.webmod.data.ProtocolType
import com.tiktok.webmod.data.ProxyNode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ProxyParser {

    fun parse(rawInput: String): ProxyNode? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("socks5://", ignoreCase = true) || trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks(trimmed)
                trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> parseHttp(trimmed)
                trimmed.contains(":") -> parseSimpleHostPort(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVless(raw: String): ProxyNode? {
        val uri = Uri.parse(raw)
        val userInfo = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remark = decodeRemark(uri.fragment ?: "")
        val sni = uri.getQueryParameter("sni") ?: host
        val security = uri.getQueryParameter("security") ?: "tls"
        val path = uri.getQueryParameter("path")

        return ProxyNode(
            protocol = ProtocolType.VLESS,
            host = host,
            port = port,
            uuid = userInfo,
            sni = sni,
            security = security,
            path = path,
            remark = remark,
            rawUri = raw
        )
    }

    private fun parseTrojan(raw: String): ProxyNode? {
        val uri = Uri.parse(raw)
        val password = uri.userInfo ?: return null
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val remark = decodeRemark(uri.fragment ?: "")
        val sni = uri.getQueryParameter("sni") ?: host
        val security = uri.getQueryParameter("security") ?: "tls"

        return ProxyNode(
            protocol = ProtocolType.TROJAN,
            host = host,
            port = port,
            password = password,
            sni = sni,
            security = security,
            remark = remark,
            rawUri = raw
        )
    }

    private fun parseShadowsocks(raw: String): ProxyNode? {
        val clean = raw.substringAfter("ss://")
        val remarkPart = clean.substringAfter("#", "")
        val remark = decodeRemark(remarkPart)
        val mainPart = clean.substringBefore("#")

        // Format 1: ss://BASE64(method:password@host:port)
        // Format 2: ss://BASE64(method:password)@host:port
        return if (mainPart.contains("@")) {
            val credsEncoded = mainPart.substringBefore("@")
            val hostPort = mainPart.substringAfter("@")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").toIntOrNull() ?: 8388
            val decodedCreds = decodeBase64(credsEncoded)
            val method = decodedCreds.substringBefore(":")
            val password = decodedCreds.substringAfter(":")

            ProxyNode(
                protocol = ProtocolType.SHADOWSOCKS,
                host = host,
                port = port,
                method = method,
                password = password,
                remark = remark,
                rawUri = raw
            )
        } else {
            val decoded = decodeBase64(mainPart)
            // decoded format: method:password@host:port
            if (decoded.contains("@") && decoded.contains(":")) {
                val creds = decoded.substringBefore("@")
                val hostPort = decoded.substringAfter("@")
                val host = hostPort.substringBefore(":")
                val port = hostPort.substringAfter(":").toIntOrNull() ?: 8388
                val method = creds.substringBefore(":")
                val password = creds.substringAfter(":")

                ProxyNode(
                    protocol = ProtocolType.SHADOWSOCKS,
                    host = host,
                    port = port,
                    method = method,
                    password = password,
                    remark = remark,
                    rawUri = raw
                )
            } else {
                null
            }
        }
    }

    private fun parseSocks(raw: String): ProxyNode? {
        val uri = Uri.parse(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 1080
        val remark = decodeRemark(uri.fragment ?: "")
        val userInfo = uri.userInfo
        val user = userInfo?.substringBefore(":")
        val pass = if (userInfo != null && userInfo.contains(":")) userInfo.substringAfter(":") else null

        return ProxyNode(
            protocol = ProtocolType.SOCKS5,
            host = host,
            port = port,
            uuid = user,
            password = pass,
            remark = remark,
            rawUri = raw
        )
    }

    private fun parseHttp(raw: String): ProxyNode? {
        val uri = Uri.parse(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 8080
        val remark = decodeRemark(uri.fragment ?: "")

        return ProxyNode(
            protocol = ProtocolType.HTTP,
            host = host,
            port = port,
            remark = remark,
            rawUri = raw
        )
    }

    private fun parseSimpleHostPort(raw: String): ProxyNode? {
        val parts = raw.split(":")
        if (parts.size == 2) {
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            return ProxyNode(
                protocol = ProtocolType.SOCKS5,
                host = host,
                port = port,
                remark = "Direct SOCKS5",
                rawUri = raw
            )
        }
        return null
    }

    private fun decodeRemark(remark: String): String {
        return try {
            URLDecoder.decode(remark, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            remark
        }
    }

    private fun decodeBase64(text: String): String {
        return try {
            var padded = text.replace("-", "+").replace("_", "/")
            while (padded.length % 4 != 0) {
                padded += "="
            }
            val bytes = Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            text
        }
    }
}
