package com.tiktok.webmod.proxy

import com.tiktok.webmod.data.ProtocolType
import com.tiktok.webmod.data.ProxyNode
import org.json.JSONArray
import org.json.JSONObject

object XrayCoreManager {

    fun generateXrayConfigJson(node: ProxyNode, localPort: Int = 10808): String {
        val root = JSONObject()

        // 1. Log configuration
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", log)

        // 2. Inbound: Local SOCKS5 proxy on 127.0.0.1:localPort
        val inbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", localPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
        }
        root.put("inbounds", JSONArray().put(inbound))

        // 3. Outbound: Configured node
        val outbound = JSONObject()
        when (node.protocol) {
            ProtocolType.VLESS -> {
                outbound.put("protocol", "vless")
                outbound.put("tag", "proxy")
                val vnext = JSONObject().apply {
                    put("address", node.host)
                    put("port", node.port)
                    val user = JSONObject().apply {
                        put("id", node.uuid ?: "")
                        put("encryption", "none")
                    }
                    put("users", JSONArray().put(user))
                }
                outbound.put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(vnext))
                })

                val streamSettings = JSONObject().apply {
                    put("network", if (node.path != null) "ws" else "tcp")
                    put("security", node.security ?: "tls")
                    if (node.security == "tls") {
                        put("tlsSettings", JSONObject().apply {
                            put("serverName", node.sni ?: node.host)
                            put("allowInsecure", false)
                        })
                    }
                    if (node.path != null) {
                        put("wsSettings", JSONObject().apply {
                            put("path", node.path)
                            put("headers", JSONObject().apply {
                                put("Host", node.sni ?: node.host)
                            })
                        })
                    }
                }
                outbound.put("streamSettings", streamSettings)
            }
            ProtocolType.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put("tag", "proxy")
                val server = JSONObject().apply {
                    put("address", node.host)
                    put("port", node.port)
                    put("password", node.password ?: "")
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(server))
                })
                outbound.put("streamSettings", JSONObject().apply {
                    put("security", node.security ?: "tls")
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", node.sni ?: node.host)
                    })
                })
            }
            ProtocolType.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("tag", "proxy")
                val server = JSONObject().apply {
                    put("address", node.host)
                    put("port", node.port)
                    put("method", node.method ?: "aes-256-gcm")
                    put("password", node.password ?: "")
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(server))
                })
            }
            ProtocolType.SOCKS5 -> {
                outbound.put("protocol", "socks")
                outbound.put("tag", "proxy")
                val server = JSONObject().apply {
                    put("address", node.host)
                    put("port", node.port)
                    if (node.uuid != null && node.password != null) {
                        val user = JSONObject().apply {
                            put("user", node.uuid)
                            put("pass", node.password)
                        }
                        put("users", JSONArray().put(user))
                    }
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(server))
                })
            }
            else -> {
                outbound.put("protocol", "freedom")
                outbound.put("tag", "direct")
            }
        }

        root.put("outbounds", JSONArray().put(outbound))

        return root.toString(2)
    }
}
