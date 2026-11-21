package svoboda3.runet.abonent0007.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import svoboda3.runet.abonent0007.split.SplitTunnelMode
import svoboda3.runet.abonent0007.split.SplitTunnelPresets
import svoboda3.runet.abonent0007.AppGraph
import svoboda3.runet.abonent0007.BuildConfig
import svoboda3.runet.abonent0007.data.Prefs
import svoboda3.runet.abonent0007.ui.theme.AccentBlue
import svoboda3.runet.abonent0007.ui.theme.AccentGreen
import svoboda3.runet.abonent0007.ui.theme.Border
import svoboda3.runet.abonent0007.ui.theme.Elevated
import svoboda3.runet.abonent0007.ui.theme.ErrorRed
import svoboda3.runet.abonent0007.ui.theme.GoodGreen
import svoboda3.runet.abonent0007.ui.method.MethodSettingsDialog
import svoboda3.runet.abonent0007.ui.method.DnsResolverPanel
import svoboda3.runet.abonent0007.ui.split.RussianDirectDialog
import svoboda3.runet.abonent0007.ui.theme.TextPrimary
import svoboda3.runet.abonent0007.ui.theme.TextSecondary
import svoboda3.runet.abonent0007.vpn.VpnPhase

/**
 * Способы подключения в Svoboda 2.0.
 * Один активный способ определяет, какой туннель будет установлен при нажатии "Подключить".
 *
 * [available] = false означает, что движок ещё не подключён. Такие режимы
 * отображаются неактивными с пометкой «в разработке» и не могут быть выбраны.
 */
enum class ConnectionMethod(val label: String, val available: Boolean) {
    AWG("AWG", true),                          // GoBackend + AmneziaWG
    WARP("WARP", true),                        // Cloudflare WARP
    DPI_DIRECT("DPI", true),                   // Локальный обход DPI (ciadpi + HEV)
    AUTO("AUTO", true),                       // Автовыбор транспорта — после DPI-1.x
}

private val VioletGradient = listOf(Color(0xFFC792EA), Color(0xFF9D6BFF), Color(0xFF7C4DFF))
private val GreenGradient = listOf(GoodGreen, AccentGreen, AccentBlue)
private val BlueGradient = listOf(Color(0xFF6FA8FF), AccentBlue, Color(0xFF5B6BFF))

/**
 * Главный экран Свободы 2.0.
 *
 * Центр: неоновая строка статуса; за ней — расходящиеся зелёные круги.
 * Пока идёт подключение — «нейронные» линии. Под статусом — плитки
 * (профиль, IP, страна, ping, график, способ подключения), каждая строка в своей плитке.
 * Меню — шторка с плитками и ползунками (серый выкл / зелёный вкл).
 *
 * Способ подключения выбирается отдельной строкой под названием профиля.
 * Доступные режимы: AWG (AmneziaWG), WARP (Cloudflare), DPI_DIRECT (локальный обход),
 * AUTO (автоматический выбор). Выбор влияет на туннель и трассировку.
 */
@Composable
fun HomeScreen(
    phase: VpnPhase,
    error: String?,
    profileLabel: String,
    exitIp: String?,
    country: String?,
    lastPing: Int?,
    pingSamples: List<Int>,
    testResult: String?,
    prefs: Prefs,
    splitEnabled: Boolean,
    excludedCount: Int,
    serverTitle: String?,
    splitProfileId: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onSplitToggle: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onOpenServers: () -> Unit,
    updateMessage: String? = null,
    onDismissUpdate: () -> Unit = {},
    onCheckUpdates: () -> Unit,
) {
    val connected = phase == VpnPhase.CONNECTED || phase == VpnPhase.DEGRADED
    val busy = phase == VpnPhase.PREPARING ||
        phase == VpnPhase.CONNECTING ||
        phase == VpnPhase.VERIFYING

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // BACK/навигация-назад в режиме меню: закрыть drawer (не выходить из приложения).
    // Выход — только с главной страницы (стандартное поведение Activity).
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Локальные зеркала настроек (пишутся в Prefs).
    var graphEnabled by remember { mutableStateOf(prefs.pingGraphEnabled) }
    var intervalSec by remember { mutableStateOf(prefs.pingIntervalMs / 1000) }
    var autoConnect by remember { mutableStateOf(prefs.autoConnect) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdateEnabled) }
    var mtu by remember { mutableStateOf(prefs.tunnelMtu) }

    var ipv6Enabled by remember { mutableStateOf(prefs.ipv6Enabled) }

    // Способ подключения (сохраняется в Prefs между запусками).
    var connectionMethod by remember {
        mutableStateOf(
            ConnectionMethod.values().firstOrNull { it.name == prefs.connectionMethod }
                ?: ConnectionMethod.AWG,
        )
    }

    // Открытое подменю тонких настроек метода (null = закрыто).
    var methodSettingsFor by remember { mutableStateOf<ConnectionMethod?>(null) }

    // Подменю «Российские приложения — напрямую».
    var showRussianDirect by remember { mutableStateOf(false) }
    var dnsText by remember { mutableStateOf(prefs.dnsServers) }
    var showDnsDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContent(
                graphEnabled = graphEnabled,
                intervalSec = intervalSec,
                autoConnect = autoConnect,
                mtu = mtu,
                ipv6Enabled = ipv6Enabled,
                dnsText = dnsText,
                splitEnabled = splitEnabled,
                excludedCount = excludedCount,
                serverTitle = serverTitle,
                splitProfileId = splitProfileId,
                connectionMethod = connectionMethod,
                onConnectionMethod = {
                    if (it.available) {
                        connectionMethod = it
                        prefs.connectionMethod = it.name
                    }
                },
                autoUpdate = autoUpdate,
                onAutoUpdate = {
                    autoUpdate = it
                    prefs.autoUpdateEnabled = it
                },
                onOpenMethodSettings = { methodSettingsFor = it },
                onOpenRussianDirect = { showRussianDirect = true },
                onGraphEnabled = {
                    graphEnabled = it
                    prefs.pingGraphEnabled = it
                },
                onIntervalSec = {
                    intervalSec = it
                    prefs.pingIntervalMs = it * 1000
                },
                onAutoConnect = {
                    autoConnect = it
                    prefs.autoConnect = it
                },
                onMtu = {
                    mtu = it
                    prefs.tunnelMtu = it
                },
                onIpv6Toggle = { v ->
                    ipv6Enabled = v
                    prefs.ipv6Enabled = v
                },
                onDnsClick = { showDnsDialog = true },
                onSplitToggle = onSplitToggle,
                onPickApps = onPickApps,
                onOpenServers = onOpenServers,
                onCheckUpdates = onCheckUpdates,
                updateMessage = updateMessage,
                onDismissUpdate = onDismissUpdate,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PulseRings(
                modifier = Modifier.fillMaxSize(),
                running = !connected,
            )
            NeuralLines(
                modifier = Modifier.fillMaxSize(),
                active = busy || connected,
            )

            // Верхняя панель: неоновый заголовок по центру.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    NeonText(
                        text = "СВОБОДА 3.0",
                        gradient = VioletGradient,
                        modifier = Modifier.weight(1f),
                        fontSize = 22.sp,
                        letterSpacing = 2.sp,
                        strokeWidth = 5.dp,
                        fillColor = Color(0xFFEDE9FF),
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = TextPrimary)
                }
            }

            // Центр: плитки ВЫШЕ, статус — строго по центру (как кольца).
            Box(modifier = Modifier.fillMaxSize()) {
                // Плитки над статусом.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 110.dp)
                        .padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TileRow(title = "Профиль", value = profileLabel.removePrefix("Профиль "))
                    if (serverTitle != null) {
                        TileRow(title = "Сервер", value = serverTitle)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Elevated.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            InfoCell(
                                label = "IP",
                                value = exitIp ?: "—",
                                modifier = Modifier.weight(1.4f),
                            )
                            InfoCell(
                                label = "Страна",
                                value = country ?: "—",
                                modifier = Modifier.weight(1f),
                            )
                            InfoCell(
                                label = "Ping",
                                value = lastPing?.let { "$it мс" } ?: "— мс",
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }

                    if (pingSamples.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Elevated.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Border),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "PING · 60 С · мс",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                )
                                LatencyGraph(
                                    samples = pingSamples,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                    }
                }

                // Статус — ровно по центру экрана.
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StatusText(phase = phase)
                }

                // Ошибки/результат теста — под статусом.
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 86.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (error != null && phase == VpnPhase.ERROR) {
                        Text(
                            text = error,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                        )
                    }
                    if (testResult != null) {
                        Text(
                            text = testResult,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                        )
                    }
                }
            }

            // Низ: способ подключения + кнопки в одну линию.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectionMethodTile(
                    selected = connectionMethod,
                    onSelect = {
                        if (it.available) {
                            connectionMethod = it
                            prefs.connectionMethod = it.name
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (phase == VpnPhase.DEGRADED) {
                        // DEGRADED: туннель есть, данных нет — явный выход и явный повтор.
                        Button(
                            onClick = onConnect,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color(0xFF062018),
                            ),
                        ) {
                            Text(
                                text = "ПОВТОРИТЬ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF3A4B5C)),
                        ) {
                            Text(
                                text = "ОТКЛЮЧИТЬ",
                                color = Color(0xFFA9B7C6),
                                maxLines = 1,
                            )
                        }
                    } else {
                        Button(
                            onClick = { if (connected) onDisconnect() else onConnect() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    busy -> Elevated
                                    connected -> AccentGreen
                                    else -> AccentGreen
                                },
                                contentColor = when {
                                    busy -> AccentBlue
                                    connected -> Color(0xFF062018)
                                    else -> Color(0xFF062018)
                                },
                            ),
                        ) {
                            Text(
                                text = when {
                                    busy -> "ПОДКЛЮЧЕНИЕ…"
                                    connected -> "ОТКЛЮЧИТЬ"
                                    else -> "ПОДКЛЮЧИТЬ"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onTest,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF3A4B5C)),
                    ) {
                        Text(
                            text = "ПРОВЕРИТЬ СВЯЗЬ",
                            color = Color(0xFFA9B7C6),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            title = { Text("DNS-серверы") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DnsResolverPanel(
                        current = dnsText,
                        onApply = { dnsText = it },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.dnsServers = dnsText
                    showDnsDialog = false
                }) { Text("СОХРАНИТЬ") }
            },
            dismissButton = {
                TextButton(onClick = { showDnsDialog = false }) { Text("ОТМЕНА") }
            },
        )
    }

    if (showRussianDirect) {
        RussianDirectDialog(
            profileId = splitProfileId,
            onDismiss = { showRussianDirect = false },
        )
    }

    methodSettingsFor?.let { method ->
        MethodSettingsDialog(
            method = method,
            mtu = mtu,
            onMtu = {
                mtu = it
                prefs.tunnelMtu = it
            },
            onOpenServers = {
                methodSettingsFor = null
                onOpenServers()
            },
            onDismiss = { methodSettingsFor = null },
        )
    }
}

@Composable
private fun DrawerContent(
    graphEnabled: Boolean,
    intervalSec: Int,
    autoConnect: Boolean,
    mtu: Int,
    ipv6Enabled: Boolean,
    dnsText: String,
    splitEnabled: Boolean,
    excludedCount: Int,
    serverTitle: String?,
    splitProfileId: String,
    connectionMethod: ConnectionMethod,
    onConnectionMethod: (ConnectionMethod) -> Unit,
    autoUpdate: Boolean,
    onAutoUpdate: (Boolean) -> Unit,
    onOpenMethodSettings: (ConnectionMethod) -> Unit,
    onOpenRussianDirect: () -> Unit,
    onGraphEnabled: (Boolean) -> Unit,
    onIntervalSec: (Int) -> Unit,
    onAutoConnect: (Boolean) -> Unit,
    onMtu: (Int) -> Unit,
    onIpv6Toggle: (Boolean) -> Unit,
    onDnsClick: () -> Unit,
    onSplitToggle: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onOpenServers: () -> Unit,
    updateMessage: String? = null,
    onDismissUpdate: () -> Unit = {},
    onCheckUpdates: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C121A))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val scope = rememberCoroutineScope()
        val initialSplit = remember(splitProfileId) { AppGraph.splitManager.resolve(splitProfileId) }
        var splitMode by remember(splitProfileId) { mutableStateOf(initialSplit.mode) }
        var presetDialog by remember { mutableStateOf(false) }
        var presetRollback by remember { mutableStateOf<Set<String>?>(null) }
        var showAbout by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonText(
                text = "СВОБОДА 3.0",
                gradient = VioletGradient,
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                letterSpacing = 2.sp,
                strokeWidth = 5.dp,
                fillColor = Color(0xFFEDE9FF),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TextPrimary)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        SectionHeader("ОБЩИЕ")

        TileRow(
            title = "График пинга",
            value = if (graphEnabled) "включён" else "выключен",
            opaque = true,
            control = { NeonSwitch(graphEnabled, onGraphEnabled) },
        )

        SliderCard(
            title = "Интервал пинга",
            valueText = "$intervalSec с",
            value = intervalSec.toFloat(),
            onValueChange = { onIntervalSec(it.toInt().coerceIn(1, 10)) },
            valueRange = 1f..10f,
            minLabel = "1 с",
            maxLabel = "10 с",
            opaque = true,
        )

        TileRow(
            title = "Автоподключение",
            value = if (autoConnect) "включено" else "выключено",
            opaque = true,
            control = { NeonSwitch(autoConnect, onAutoConnect) },
        )

        TileRow(
            title = "Автообновление",
            value = if (autoUpdate) "проверять при запуске" else "выключено",
            opaque = true,
            control = { NeonSwitch(autoUpdate, onAutoUpdate) },
        )

        SectionHeader("ПОДКЛЮЧЕНИЕ")

        ConnectionMethodTile(
            selected = connectionMethod,
            onSelect = onConnectionMethod,
        )

        if (connectionMethod == ConnectionMethod.AWG) {
            TileRow(
                title = "Сервер AmneziaWG",
                value = serverTitle ?: "не выбран — подключение через WARP",
                opaque = true,
                onClick = onOpenServers,
            )
        }

        if (connectionMethod.available) {
            MtuCard(mtu = mtu, onMtu = onMtu)
        } else {
            TileRow(
                title = "Движок ${connectionMethod.label}",
                value = "не подключён · в разработке",
                opaque = true,
            )
        }

        SectionHeader("НАСТРОЙКИ МЕТОДОВ")

        ConnectionMethod.values().forEach { method ->
            TileRow(
                title = "${method.label} · тонкие настройки",
                value = when (method) {
                    ConnectionMethod.AWG -> "серверы, генератор .conf, MTU"
                    ConnectionMethod.WARP -> "регистрация, endpoint, MTU"
                    ConnectionMethod.DPI_DIRECT -> "стратегии BDP, SOCKS-порт, генератор"
                    ConnectionMethod.AUTO -> "политика и резервный транспорт"
                },
                opaque = true,
                onClick = { onOpenMethodSettings(method) },
            )
        }

        SectionHeader("SPLIT-TUNNEL")

        TileRow(
            title = "Режим split-tunnel",
            value = when (splitMode) {
                SplitTunnelMode.ALL -> "весь трафик через VPN"
                SplitTunnelMode.ONLY_SELECTED -> "только выбранные через VPN"
                SplitTunnelMode.ALL_EXCEPT_SELECTED -> "все кроме выбранных"
            },
            opaque = true,
            onClick = {
                val next = when (splitMode) {
                    SplitTunnelMode.ALL -> SplitTunnelMode.ONLY_SELECTED
                    SplitTunnelMode.ONLY_SELECTED -> SplitTunnelMode.ALL_EXCEPT_SELECTED
                    SplitTunnelMode.ALL_EXCEPT_SELECTED -> SplitTunnelMode.ALL
                }
                splitMode = next
                val cfg = AppGraph.splitManager.resolve(splitProfileId).copy(mode = next)
                AppGraph.splitManager.update(splitProfileId, cfg)
                if (AppGraph.vpn.isConnected) scope.launch { AppGraph.reconnect.userConnect(false) }
            },
        )

        TileRow(
            title = "Российские приложения — напрямую",
            value = "банки, госуслуги, веб-ресурсы (direct)",
            opaque = true,
            onClick = onOpenRussianDirect,
        )

        TileRow(
            title = "Пресет: Российские приложения",
            value = "исключить из VPN",
            opaque = true,
            onClick = { presetDialog = true },
        )

        if (presetDialog) {
            val current = AppGraph.prefs.globalDirectApps
            val preset = SplitTunnelPresets.russianApps.packages
            val toAdd = preset - current
            val toRemove = current - preset
            AlertDialog(
                onDismissRequest = { presetDialog = false },
                title = { Text("Пресет: Российские приложения") },
                text = {
                    Text(
                        "Глобальный direct-список (один на все методы).\n" +
                            "Сейчас: ${current.size} пакетов.\n" +
                            "Пресет: ${preset.size} пакетов.\n" +
                            "Добавится: ${toAdd.size} · удалится: ${toRemove.size}.\n\n" +
                            "«Объединить» — добавить пресет к вашему списку (ваши пакеты сохраняются).\n" +
                            "«Заменить» — полностью перезаписать список пресетом.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        presetRollback = current
                        val merged = current + preset
                        AppGraph.prefs.globalDirectApps = merged
                        if (AppGraph.vpn.isConnected) scope.launch { AppGraph.reconnect.userConnect(false) }
                        presetDialog = false
                    }) { Text("ОБЪЕДИНИТЬ") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        presetRollback = current
                        AppGraph.prefs.globalDirectApps = preset
                        if (AppGraph.vpn.isConnected) scope.launch { AppGraph.reconnect.userConnect(false) }
                        presetDialog = false
                    }) { Text("ЗАМЕНИТЬ") }
                },
            )
        }

        if (presetRollback != null) {
            TextButton(onClick = {
                val base = presetRollback ?: return@TextButton
                presetRollback = null
                AppGraph.prefs.globalDirectApps = base
                if (AppGraph.vpn.isConnected) scope.launch { AppGraph.reconnect.userConnect(false) }
            }) { Text("ОТМЕНИТЬ ПРЕСЕТ", color = AccentGreen) }
        }

        if (showAbout) {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("О приложении") },
                text = {
                    Column {
                        Text(
                            "Свобода 2.0 · v${BuildConfig.VERSION_NAME}",
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Разработчик: Птицын Юрий Юрьевич", color = TextPrimary, fontSize = 13.sp)
                        Text("© 2026", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Поддержать проект", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("T-Банк, Сбер — по номеру:", color = TextSecondary, fontSize = 12.sp)
                        TextButton(onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("donate", "+79684520007"))
                            android.widget.Toast.makeText(context, "Номер скопирован", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("+7 968 452-00-07", color = AccentGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Нажмите на номер, чтобы скопировать", color = TextSecondary, fontSize = 10.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) { Text("ЗАКРЫТЬ") }
                },
            )
        }

        if (updateMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissUpdate,
                title = { Text("Обновления") },
                text = { Text(updateMessage ?: "", color = TextPrimary, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = onDismissUpdate) { Text("ЗАКРЫТЬ") }
                },
            )
        }

        SectionHeader("СЕТЬ")

        TileRow(
            title = "IPv6 через VPN",
            value = if (ipv6Enabled) "включён" else "выключен",
            opaque = true,
            control = { NeonSwitch(ipv6Enabled, onIpv6Toggle) },
        )

        TileRow(
            title = "DNS",
            value = dnsText.ifBlank { "по умолчанию" },
            opaque = true,
            onClick = onDnsClick,
        )

        SectionHeader("ПРОЧЕЕ")

        TileRow(
            title = "О приложении",
            value = "разработчик, донаты",
            opaque = true,
            onClick = { showAbout = true },
        )

        TileRow(
            title = "Обновления",
            value = "GitHub Releases",
            opaque = true,
            onClick = onCheckUpdates,
        )

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Border),
        ) {
            Text("ЗАКРЫТЬ МЕНЮ", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/**
 * MTU: слайдер 1200..1480 + быстрые кнопки с шагом 20 от 1380 до 1480.
 * Кнопки в одну линию за счёт мелкого шрифта.
 */
@Composable
private fun MtuCard(mtu: Int, onMtu: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Elevated,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MTU",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(text = "$mtu", color = TextSecondary, fontSize = 12.sp)
            }
            Slider(
                value = mtu.toFloat(),
                onValueChange = { onMtu(it.toInt().coerceIn(1200, 1480)) },
                valueRange = 1200f..1480f,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("1200", color = TextSecondary, fontSize = 10.sp)
                Text("1480", color = TextSecondary, fontSize = 10.sp)
            }
            Text(
                text = svoboda3.runet.abonent0007.ui.method.MTU_DESCRIPTION,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MTU_PRESETS.forEach { value ->
                    MtuPresetChip(
                        value = value,
                        selected = mtu == value,
                        onClick = { onMtu(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val MTU_PRESETS = listOf(1380, 1400, 1420, 1440, 1460, 1480)

@Composable
private fun MtuPresetChip(
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AccentGreen.copy(alpha = 0.18f) else Elevated.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (selected) AccentGreen else Border),
    ) {
        Text(
            text = value.toString(),
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
        )
    }
}

/**
 * Заголовок секции в меню: настройки сгруппированы по назначению,
 * чтобы параметры разных способов подключения не смешивались.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/**
 * Плитка выбора способа подключения.
 * AWG и WARP доступны; DPI и AUTO помечены «в разработке» и заблокированы,
 * пока соответствующие native-движки не подключены.
 */
@Composable
private fun ConnectionMethodTile(
    selected: ConnectionMethod,
    onSelect: (ConnectionMethod) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Elevated.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Способ подключения",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (selected.available) selected.label else "${selected.label} · в разработке",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ConnectionMethod.values().forEach { method ->
                    MethodChip(
                        label = method.label,
                        selected = selected == method,
                        enabled = method.available,
                        onClick = { onSelect(method) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        selected && enabled -> AccentGreen
        selected -> AccentBlue.copy(alpha = 0.6f)
        else -> Border
    }
    Surface(
        modifier = modifier.then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        ),
        shape = RoundedCornerShape(10.dp),
        color = when {
            selected && enabled -> AccentGreen.copy(alpha = 0.16f)
            selected -> Elevated.copy(alpha = 0.9f)
            else -> Elevated.copy(alpha = 0.35f)
        },
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TextSecondary.copy(alpha = 0.5f)
                selected -> TextPrimary
                else -> TextSecondary
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusText(phase: VpnPhase) {
    when (phase) {
        VpnPhase.CONNECTED -> {
            // «Пульс сердца»: перелив радугой + двукратное биение масштаба.
            val heart = rememberInfiniteTransition(label = "heart")
            val hue by heart.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3600, easing = androidx.compose.animation.core.LinearEasing),
                ),
                label = "heartHue",
            )
            val beat by heart.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "heartBeat",
            )
            val scale = 1f + 0.07f * kotlin.math.abs(
                kotlin.math.sin(beat * Math.PI * 2.0),
            ).toFloat()
            val rainbow = (0 until 5).map { i ->
                val h = (hue + i * 45f) % 360f
                Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.95f, 1f)))
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                NeonText(
                    text = "ПОДКЛЮЧЕНО",
                    gradient = rainbow,
                    fontSize = 32.sp,
                    letterSpacing = 3.sp,
                    strokeWidth = 7.dp,
                    glowAlpha = 1f,
                    fillColor = Color.White,
                )
            }
        }

        VpnPhase.DEGRADED -> NeonText(
            text = "НЕТ ИНТЕРНЕТА",
            gradient = listOf(Color(0xFFFFD166), Color(0xFFFFB703), Color(0xFFFF8C42)),
            fontSize = 30.sp,
            letterSpacing = 2.sp,
            strokeWidth = 6.dp,
            glowAlpha = 0.6f,
            fillColor = Color(0xFFFFF4E0),
        )

        VpnPhase.PREPARING, VpnPhase.CONNECTING, VpnPhase.VERIFYING -> {
            // Ярче и «сочнее»: почти без прозрачности, сильное свечение.
            val pulse by rememberInfiniteTransition(label = "connPulse").animateFloat(
                initialValue = 0.85f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "connPulseAlpha",
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.graphicsLayer { alpha = pulse },
            ) {
                NeonText(
                    text = "ПОДКЛЮЧЕНИЕ…",
                    gradient = BlueGradient,
                    fontSize = 30.sp,
                    letterSpacing = 3.sp,
                    strokeWidth = 7.dp,
                    glowAlpha = 1f,
                    fillColor = Color(0xFFF2F8FF),
                )
            }
        }

        VpnPhase.ERROR -> Text(
            text = "ОШИБКА",
            color = ErrorRed,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        )

        else -> Text(
            text = "ОТКЛЮЧЕНО",
            color = TextSecondary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        )
    }
}
