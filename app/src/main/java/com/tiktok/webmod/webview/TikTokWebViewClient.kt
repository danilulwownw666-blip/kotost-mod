package com.tiktok.webmod.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object TikTokWebViewHelper {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.231105.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    const val TIKTOK_URL = "https://www.tiktok.com/"

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(webView: WebView, region: String, onProgressChange: (Int) -> Unit = {}) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = DEFAULT_USER_AGENT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Apply region cookies
        applyRegionCookies(region)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChange(newProgress)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Keep TikTok domains within WebView
                return if (url.contains("tiktok.com") || url.contains("tiktokcdn.com") || url.contains("byteoversea.com")) {
                    false
                } else {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                applyRegionCookies(region)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
            }
        }
    }

    fun applyRegionCookies(region: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(null, true)

        val regUpper = region.uppercase()
        val regLower = region.lowercase()

        val cookies = listOf(
            "country_code=$regUpper; Domain=.tiktok.com; Path=/; Secure; SameSite=None",
            "my_country_code=$regUpper; Domain=.tiktok.com; Path=/; Secure; SameSite=None",
            "store-country-code=$regLower; Domain=.tiktok.com; Path=/; Secure; SameSite=None",
            "store-country-code-src=did; Domain=.tiktok.com; Path=/; Secure; SameSite=None",
            "tt_chain_token=valid; Domain=.tiktok.com; Path=/; Secure; SameSite=None"
        )

        for (cookie in cookies) {
            cookieManager.setCookie("https://www.tiktok.com", cookie)
            cookieManager.setCookie(".tiktok.com", cookie)
        }
        cookieManager.flush()
    }

    fun extractCurrentVideoUrl(webView: WebView, onResult: (String) -> Unit) {
        val script = """
            (function() {
                var videos = Array.from(document.querySelectorAll('video'));
                // 1. Look for playing or in-view video
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    var rect = v.getBoundingClientRect();
                    if (rect.top >= -100 && rect.bottom <= (window.innerHeight || 1200) + 100) {
                        if (v.src && v.src.indexOf('http') === 0 && v.src.indexOf('blob:') !== 0) {
                            return v.src;
                        }
                        var s = v.querySelector('source');
                        if (s && s.src && s.src.indexOf('http') === 0) {
                            return s.src;
                        }
                    }
                }
                // 2. Fallback to any valid video src
                for (var j = 0; j < videos.length; j++) {
                    var v2 = videos[j];
                    if (v2.src && v2.src.indexOf('http') === 0 && v2.src.indexOf('blob:') !== 0) {
                        return v2.src;
                    }
                    var s2 = v2.querySelector('source');
                    if (s2 && s2.src && s2.src.indexOf('http') === 0) {
                        return s2.src;
                    }
                }
                return '';
            })()
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            val clean = result?.replace("\"", "")?.trim() ?: ""
            onResult(clean)
        }
    }
}
