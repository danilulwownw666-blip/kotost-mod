package com.tiktok.webmod.ui

import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tiktok.webmod.data.ConfigPreferences
import com.tiktok.webmod.proxy.ProxyManager
import com.tiktok.webmod.ui.theme.*
import com.tiktok.webmod.util.VideoDownloader
import com.tiktok.webmod.webview.TikTokWebViewHelper

data class RegionItem(val code: String, val name: String, val flag: String)
data class MangoPreset(val name: String, val region: String, val config: String, val flag: String)

val REGIONS = listOf(
    RegionItem("US", "США", "🇺🇸"),
    RegionItem("KZ", "Казахстан", "🇰🇿"),
    RegionItem("DE", "Германия", "🇩🇪"),
    RegionItem("TR", "Турция", "🇹🇷"),
    RegionItem("JP", "Япония", "🇯🇵"),
    RegionItem("GB", "Великобритания", "🇬🇧")
)

val MANGO_PRESETS = listOf(
    MangoPreset("Mango US", "US", "socks5://184.178.172.5:1080#Mango-US", "🇺🇸"),
    MangoPreset("Mango NL", "DE", "socks5://149.202.88.66:1080#Mango-NL", "🇳🇱"),
    MangoPreset("Mango DE", "DE", "socks5://159.69.153.111:1080#Mango-DE", "🇩🇪"),
    MangoPreset("Mango KZ", "KZ", "socks5://95.56.241.22:1080#Mango-KZ", "🇰🇿")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { ConfigPreferences(context) }

    var configText by remember { mutableStateOf(prefs.proxyUri) }
    var selectedRegion by remember { mutableStateOf(prefs.selectedRegion) }
    var localPortText by remember { mutableStateOf(prefs.localPort.toString()) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showRegionSheet by remember { mutableStateOf(false) }

    val isConnected by ProxyManager.isConnected.collectAsState()
    val statusMsg by ProxyManager.statusMessage.collectAsState()
    val currentNode by ProxyManager.currentNode.collectAsState()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableIntStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }

    // Auto-connect proxy on startup if config is saved or if was active
    LaunchedEffect(Unit) {
        if (configText.isBlank()) {
            configText = MANGO_PRESETS.first().config
            prefs.proxyUri = configText
        }
        if (prefs.isProxyActive || configText.isNotBlank()) {
            ProxyManager.connect(context, configText) { _, _ -> }
        }
    }

    // Handle Back button inside TikTok
    BackHandler {
        if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (showRegionSheet) {
            showRegionSheet = false
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TikTokBlack)
    ) {
        // 1. FULLSCREEN IMMERSIVE TIKTOK
        Column(modifier = Modifier.fillMaxSize()) {
            if (webProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { webProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MangoOrange,
                    trackColor = TikTokDarkGray
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        TikTokWebViewHelper.configureWebView(
                            webView = this,
                            region = selectedRegion,
                            onProgressChange = { progress ->
                                webProgress = progress
                            }
                        )
                        loadUrl(TikTokWebViewHelper.TIKTOK_URL)
                        webViewInstance = this
                    }
                },
                update = { wv ->
                    webViewInstance = wv
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. MINIMAL TOP FLOATING BAR (Region pill + Settings button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Region switch pill (tap to quickly change region)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = TikTokBlack.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MangoOrange),
                modifier = Modifier.clickable { showRegionSheet = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusGreen else StatusRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🥭 $selectedRegion",
                        color = MangoYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick actions (Reload & Settings)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = TikTokBlack.copy(alpha = 0.75f),
                    modifier = Modifier.size(36.dp).clickable { webViewInstance?.reload() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = TikTokWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = TikTokBlack.copy(alpha = 0.75f),
                    modifier = Modifier.size(36.dp).clickable { showSettingsSheet = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = TikTokWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3. FLOATING ACTION BUTTON: DOWNLOAD VIDEO
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    val wv = webViewInstance
                    if (wv != null && !isDownloading) {
                        isDownloading = true
                        TikTokWebViewHelper.extractCurrentVideoUrl(wv) { foundUrl ->
                            isDownloading = false
                            if (foundUrl.isNotBlank()) {
                                VideoDownloader.downloadVideo(
                                    context = context,
                                    videoUrl = foundUrl,
                                    userAgent = TikTokWebViewHelper.DEFAULT_USER_AGENT,
                                    onSuccess = {
                                        Toast.makeText(context, "🥭 Видео скачивается!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Видео не обнаружено", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                containerColor = MangoOrange,
                contentColor = TikTokWhite,
                icon = {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TikTokWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = "Скачать")
                    }
                },
                text = { Text("🥭 Скачать", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // 4. BOTTOM SHEET: REGION SELECTOR
        if (showRegionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRegionSheet = false },
                containerColor = TikTokDarkGray
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "🌍 Выберите регион TikTok",
                        color = TikTokWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    REGIONS.forEach { reg ->
                        val isSelected = reg.code == selectedRegion
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MangoOrange.copy(alpha = 0.2f) else TikTokCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MangoOrange else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedRegion = reg.code
                                    prefs.selectedRegion = reg.code
                                    TikTokWebViewHelper.applyRegionCookies(reg.code)
                                    webViewInstance?.reload()
                                    showRegionSheet = false
                                    Toast.makeText(context, "Регион изменен на ${reg.name}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = reg.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${reg.name} (${reg.code})",
                                    color = if (isSelected) MangoYellow else TikTokWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // 5. BOTTOM SHEET: PROXY SETTINGS
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = TikTokDarkGray
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "⚙️ Настройки обхода и Прокси",
                        color = TikTokWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMsg,
                        color = if (isConnected) StatusGreen else TikTokGray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset buttons
                    Text(
                        text = "🥭 Быстрые сервера (1 клик):",
                        color = MangoOrange,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MANGO_PRESETS.forEach { p ->
                            FilterChip(
                                selected = configText == p.config,
                                onClick = {
                                    configText = p.config
                                    selectedRegion = p.region
                                    prefs.proxyUri = p.config
                                    prefs.selectedRegion = p.region
                                    ProxyManager.connect(context, p.config) { _, _ -> }
                                    TikTokWebViewHelper.applyRegionCookies(p.region)
                                    webViewInstance?.reload()
                                },
                                label = { Text("${p.flag} ${p.name}", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual Config Input
                    OutlinedTextField(
                        value = configText,
                        onValueChange = {
                            configText = it
                            prefs.proxyUri = it
                        },
                        label = { Text("Ссылка на конфиг (VLESS/Trojan/SS/SOCKS5)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MangoOrange,
                            unfocusedBorderColor = TikTokCardBg,
                            focusedTextColor = TikTokWhite,
                            unfocusedTextColor = TikTokWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cb?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    configText = clip.trim()
                                    prefs.proxyUri = configText
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Вставить", tint = MangoYellow)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (isConnected) {
                                ProxyManager.disconnect(context)
                            } else {
                                ProxyManager.connect(context, configText) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ProxyManager.isConnected.value) {
                                        webViewInstance?.reload()
                                        showSettingsSheet = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) StatusRed else MangoOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isConnected) "Отключить прокси" else "Подключить и обновить",
                            fontWeight = FontWeight.Bold,
                            color = TikTokWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
