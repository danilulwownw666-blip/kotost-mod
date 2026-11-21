package com.tiktok.webmod.ui

import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tiktok.webmod.data.ConfigPreferences
import com.tiktok.webmod.proxy.ProxyManager
import com.tiktok.webmod.ui.theme.*
import com.tiktok.webmod.util.VideoDownloader
import com.tiktok.webmod.webview.TikTokWebViewHelper

enum class AppTab {
    SETTINGS,
    TIKTOK
}

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

    var currentTab by remember { mutableStateOf(if (prefs.isProxyActive) AppTab.TIKTOK else AppTab.SETTINGS) }
    var configText by remember { mutableStateOf(prefs.proxyUri) }
    var selectedRegion by remember { mutableStateOf(prefs.selectedRegion) }
    var localPortText by remember { mutableStateOf(prefs.localPort.toString()) }

    val isConnected by ProxyManager.isConnected.collectAsState()
    val statusMsg by ProxyManager.statusMessage.collectAsState()
    val currentNode by ProxyManager.currentNode.collectAsState()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableIntStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }

    // Intercept back button when in TikTok tab
    BackHandler(enabled = currentTab == AppTab.TIKTOK) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            currentTab = AppTab.SETTINGS
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) StatusGreen else StatusRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kotost Mod 🥭",
                            fontWeight = FontWeight.Bold,
                            color = TikTokWhite,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MangoOrange.copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MangoOrange)
                        ) {
                            Text(
                                text = "$selectedRegion 🥭",
                                color = MangoYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (currentTab == AppTab.TIKTOK) {
                        IconButton(onClick = {
                            if (webViewInstance?.canGoBack() == true) webViewInstance?.goBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TikTokWhite)
                        }
                        IconButton(onClick = {
                            webViewInstance?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = TikTokWhite)
                        }
                        IconButton(onClick = { currentTab = AppTab.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TikTokWhite)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TikTokBlack,
                    titleContentColor = TikTokWhite
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = TikTokDarkGray,
                contentColor = TikTokWhite
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Прокси") },
                    label = { Text("Настройки") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MangoOrange,
                        selectedTextColor = MangoOrange,
                        indicatorColor = TikTokSurfaceLight
                    )
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.TIKTOK,
                    onClick = {
                        if (!isConnected && configText.isNotBlank()) {
                            ProxyManager.connect(context, configText) { _, _ -> }
                        }
                        currentTab = AppTab.TIKTOK
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "TikTok") },
                    label = { Text("TikTok") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TikTokRed,
                        selectedTextColor = TikTokRed,
                        indicatorColor = TikTokSurfaceLight
                    )
                )
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.TIKTOK) {
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
                                            Toast.makeText(context, "🥭 Загрузка началась!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "Видео не обнаружено на экране", Toast.LENGTH_SHORT).show()
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
                    text = { Text("🥭 Скачать видео", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(TikTokBlack)
        ) {
            when (currentTab) {
                AppTab.SETTINGS -> {
                    SettingsView(
                        configText = configText,
                        onConfigChange = {
                            configText = it
                            prefs.proxyUri = it
                        },
                        selectedRegion = selectedRegion,
                        onRegionSelect = { reg ->
                            selectedRegion = reg
                            prefs.selectedRegion = reg
                            TikTokWebViewHelper.applyRegionCookies(reg)
                            Toast.makeText(context, "Регион изменен на $reg", Toast.LENGTH_SHORT).show()
                        },
                        localPort = localPortText,
                        onPortChange = {
                            localPortText = it
                            it.toIntOrNull()?.let { p -> prefs.localPort = p }
                        },
                        isConnected = isConnected,
                        statusMessage = statusMsg,
                        nodeInfo = currentNode?.getSummary(),
                        onConnectClick = {
                            if (isConnected) {
                                ProxyManager.disconnect(context)
                            } else {
                                ProxyManager.connect(context, configText) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        currentTab = AppTab.TIKTOK
                                    }
                                }
                            }
                        },
                        onOpenTikTokClick = {
                            currentTab = AppTab.TIKTOK
                        }
                    )
                }

                AppTab.TIKTOK -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (webProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { webProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
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
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    configText: String,
    onConfigChange: (String) -> Unit,
    selectedRegion: String,
    onRegionSelect: (String) -> Unit,
    localPort: String,
    onPortChange: (String) -> Unit,
    isConnected: Boolean,
    statusMessage: String,
    nodeInfo: String?,
    onConnectClick: () -> Unit,
    onOpenTikTokClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Статус обхода 🥭",
                        fontWeight = FontWeight.Bold,
                        color = TikTokWhite,
                        fontSize = 16.sp
                    )
                    Surface(
                        color = if (isConnected) StatusGreen.copy(alpha = 0.2f) else StatusRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isConnected) "АКТИВЕН" else "ОТКЛЮЧЕН",
                            color = if (isConnected) StatusGreen else StatusRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusMessage,
                    color = TikTokGray,
                    fontSize = 13.sp
                )

                if (nodeInfo != null && isConnected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Сервер: $nodeInfo",
                        color = MangoYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Mango Presets Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🥭 Mango Готовые Сервера",
                        fontWeight = FontWeight.Bold,
                        color = MangoOrange,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MangoYellow.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "1 КЛИК",
                            color = MangoYellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Нажми на сервер, чтобы применить конфиг и регион без ручного ввода:",
                    color = TikTokGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MANGO_PRESETS.forEach { preset ->
                        val isSelected = configText == preset.config
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onConfigChange(preset.config)
                                onRegionSelect(preset.region)
                                Toast.makeText(context, "🥭 Выбран ${preset.name} (${preset.flag})", Toast.LENGTH_SHORT).show()
                            },
                            label = {
                                Text(
                                    text = "${preset.flag} ${preset.name}",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MangoOrange.copy(alpha = 0.3f),
                                selectedLabelColor = MangoYellow,
                                containerColor = TikTokCardBg,
                                labelColor = TikTokWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MangoOrange else Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        // Region Selector Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🌍 Выбор региона (Подмена заголовков и куки)",
                    fontWeight = FontWeight.Bold,
                    color = TikTokWhite,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    REGIONS.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { region ->
                                val isSelected = region.code == selectedRegion
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onRegionSelect(region.code) },
                                    label = {
                                        Text(
                                            text = "${region.flag} ${region.code}",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TikTokCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = TikTokCyan,
                                        containerColor = TikTokCardBg,
                                        labelColor = TikTokWhite
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) TikTokCyan else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Proxy Config Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TikTokDarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔑 Ручной ввод (VLESS / Trojan / SS / SOCKS5)",
                        fontWeight = FontWeight.Bold,
                        color = TikTokWhite,
                        fontSize = 15.sp
                    )

                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            onConfigChange(clip.trim())
                            Toast.makeText(context, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Вставить", tint = MangoYellow)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = configText,
                    onValueChange = onConfigChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "vless://... / trojan://... / ss://... / socks5://...",
                            color = TikTokGray,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MangoOrange,
                        unfocusedBorderColor = TikTokCardBg,
                        focusedTextColor = TikTokWhite,
                        unfocusedTextColor = TikTokWhite,
                        cursorColor = MangoOrange
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (configText.isNotEmpty()) {
                            IconButton(onClick = { onConfigChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = TikTokGray)
                            }
                        }
                    },
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Local SOCKS5 port configuration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Локальный порт ядра:",
                        color = TikTokGray,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = onPortChange,
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MangoOrange,
                            unfocusedBorderColor = TikTokCardBg,
                            focusedTextColor = TikTokWhite,
                            unfocusedTextColor = TikTokWhite
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Action Buttons
        Button(
            onClick = onConnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) StatusRed else MangoOrange
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Close else Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = TikTokWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "Отключить прокси" else "🥭 Подключить Mango Обход",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TikTokWhite
                )
            }
        }

        OutlinedButton(
            onClick = onOpenTikTokClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, TikTokCyan),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TikTokCyan)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TikTokCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Открыть TikTok",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TikTokCyan
                )
            }
        }
    }
}
