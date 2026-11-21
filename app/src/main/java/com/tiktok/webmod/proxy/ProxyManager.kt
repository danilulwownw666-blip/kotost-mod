package com.tiktok.webmod.proxy

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.tiktok.webmod.data.ConfigPreferences
import com.tiktok.webmod.data.ProtocolType
import com.tiktok.webmod.data.ProxyNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

object ProxyManager {
    private const val TAG = "ProxyManager"
    private var localSocksServer: LocalSocksServer? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _statusMessage = MutableStateFlow("Готов к подключению")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentNode = MutableStateFlow<ProxyNode?>(null)
    val currentNode: StateFlow<ProxyNode?> = _currentNode.asStateFlow()

    fun connect(context: Context, configUri: String, onResult: (Boolean, String) -> Unit) {
        val prefs = ConfigPreferences(context)
        val port = prefs.localPort

        if (configUri.isBlank()) {
            val msg = "Введите ссылку на конфиг (VLESS/Trojan/SS/Socks5)"
            _statusMessage.value = msg
            onResult(false, msg)
            return
        }

        val node = ProxyParser.parse(configUri)
        if (node == null) {
            val msg = "Неверный формат конфига. Поддерживаются vless://, trojan://, ss://, socks5://"
            _statusMessage.value = msg
            onResult(false, msg)
            return
        }

        _currentNode.value = node
        _statusMessage.value = "Запуск локального SOCKS5 ядра (порт $port)..."

        executor.execute {
            // Stop any existing server
            localSocksServer?.stop()

            // Start Local SOCKS5 Proxy Server
            localSocksServer = LocalSocksServer(localPort = port, upstreamNode = node)
            val started = localSocksServer?.start() == true

            if (!started) {
                _statusMessage.value = "Ошибка запуска локального SOCKS5 ядра"
                _isConnected.value = false
                onResult(false, "Не удалось открыть локальный порт $port")
                return@execute
            }

            // Route WebView traffic through local SOCKS5 proxy
            applyProxyToWebView(port) { webViewSuccess ->
                if (webViewSuccess) {
                    _isConnected.value = true
                    prefs.isProxyActive = true
                    prefs.proxyUri = configUri
                    val msg = "Подключено к ${node.protocol} (${node.host})"
                    _statusMessage.value = msg
                    onResult(true, msg)
                } else {
                    _isConnected.value = false
                    localSocksServer?.stop()
                    val msg = "WebView ProxyOverride не поддерживается на данном устройстве"
                    _statusMessage.value = msg
                    onResult(false, msg)
                }
            }
        }
    }

    private fun applyProxyToWebView(localPort: Int, onResult: (Boolean) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "PROXY_OVERRIDE is not supported on this WebView version")
            onResult(false)
            return
        }

        try {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("socks://127.0.0.1:$localPort")
                .addDirect()
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                { command -> command.run() },
                {
                    Log.i(TAG, "WebView proxy override set to socks://127.0.0.1:$localPort")
                    onResult(true)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting proxy override", e)
            onResult(false)
        }
    }

    fun disconnect(context: Context, onComplete: () -> Unit = {}) {
        val prefs = ConfigPreferences(context)
        prefs.isProxyActive = false

        executor.execute {
            localSocksServer?.stop()
            localSocksServer = null

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                try {
                    ProxyController.getInstance().clearProxyOverride(
                        { command -> command.run() },
                        {
                            Log.i(TAG, "WebView proxy override cleared")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing proxy override", e)
                }
            }

            _isConnected.value = false
            _statusMessage.value = "Прокси отключен"
            onComplete()
        }
    }
}
