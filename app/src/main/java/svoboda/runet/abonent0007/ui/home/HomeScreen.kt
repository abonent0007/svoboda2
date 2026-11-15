package svoboda.runet.abonent0007.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.BuildConfig
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.ui.theme.AccentBlue
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.ErrorRed
import svoboda.runet.abonent0007.ui.theme.GoodGreen
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary
import svoboda.runet.abonent0007.vpn.VpnPhase

private val VioletGradient = listOf(Color(0xFFC792EA), Color(0xFF9D6BFF), Color(0xFF7C4DFF))
private val GreenGradient = listOf(GoodGreen, AccentGreen, AccentBlue)
private val BlueGradient = listOf(Color(0xFF6FA8FF), AccentBlue, Color(0xFF5B6BFF))

/**
 * Главный экран Свободы 2.0.
 *
 * Центр: неоновая строка статуса; за ней — расходящиеся зелёные круги.
 * Пока идёт подключение — «нейронные» линии. Под статусом — плитки
 * (профиль, IP, страна, ping, график), каждая строка в своей плитке.
 * Меню — шторка с плитками и ползунками (серый выкл / зелёный вкл).
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
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onSplitToggle: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onOpenServers: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val connected = phase == VpnPhase.CONNECTED
    val busy = phase == VpnPhase.PREPARING ||
        phase == VpnPhase.CONNECTING ||
        phase == VpnPhase.VERIFYING

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Локальные зеркала настроек (пишутся в Prefs).
    var graphEnabled by remember { mutableStateOf(prefs.pingGraphEnabled) }
    var intervalSec by remember { mutableStateOf(prefs.pingIntervalMs / 1000) }
    var autoConnect by remember { mutableStateOf(prefs.autoConnect) }
    var mtu by remember { mutableStateOf(prefs.tunnelMtu) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContent(
                graphEnabled = graphEnabled,
                intervalSec = intervalSec,
                autoConnect = autoConnect,
                mtu = mtu,
                splitEnabled = splitEnabled,
                excludedCount = excludedCount,
                serverTitle = serverTitle,
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
                onSplitToggle = onSplitToggle,
                onPickApps = onPickApps,
                onOpenServers = onOpenServers,
                onCheckUpdates = onCheckUpdates,
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
                active = busy,
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
                        text = "СВОБОДА 2.0",
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

            // Низ: кнопки.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Button(
                    onClick = { if (connected) onDisconnect() else onConnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF3A4B5C)),
                ) {
                    Text(
                        text = "ПРОВЕРИТЬ СВЯЗЬ",
                        color = Color(0xFFA9B7C6),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    graphEnabled: Boolean,
    intervalSec: Int,
    autoConnect: Boolean,
    mtu: Int,
    splitEnabled: Boolean,
    excludedCount: Int,
    serverTitle: String?,
    onGraphEnabled: (Boolean) -> Unit,
    onIntervalSec: (Int) -> Unit,
    onAutoConnect: (Boolean) -> Unit,
    onMtu: (Int) -> Unit,
    onSplitToggle: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onOpenServers: () -> Unit,
    onCheckUpdates: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C121A))
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeonText(
                text = "СВОБОДА 2.0",
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
            title = "Раздельное туннелирование",
            value = when {
                excludedCount > 0 -> "$excludedCount прил. · выбрать"
                else -> "выбрать приложения"
            },
            opaque = true,
            onClick = onPickApps,
            control = { NeonSwitch(splitEnabled, onSplitToggle) },
        )

        TileRow(
            title = "Сервер AmneziaWG",
            value = serverTitle ?: "WARP · Cloudflare",
            opaque = true,
            onClick = onOpenServers,
        )

        SliderCard(
            title = "MTU",
            valueText = "$mtu",
            value = mtu.toFloat(),
            onValueChange = { onMtu(it.toInt().coerceIn(1200, 1420)) },
            valueRange = 1200f..1420f,
            minLabel = "1200",
            maxLabel = "1420",
            opaque = true,
        )

        TileRow(
            title = "О приложении",
            value = "Свобода 2.0 · v${BuildConfig.VERSION_NAME}",
            opaque = true,
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
        VpnPhase.CONNECTED -> NeonText(
            text = "ПОДКЛЮЧЕНО",
            gradient = GreenGradient,
            fontSize = 32.sp,
            letterSpacing = 3.sp,
            strokeWidth = 7.dp,
            glowAlpha = 0.9f,
            fillColor = Color(0xFFE9FFF5),
        )

        VpnPhase.PREPARING, VpnPhase.CONNECTING, VpnPhase.VERIFYING -> {
            val pulse by rememberInfiniteTransition(label = "connPulse").animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
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
                    strokeWidth = 6.dp,
                    fillColor = Color(0xFFE9F2FF),
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
