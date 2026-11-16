package svoboda.runet.abonent0007.ui.method

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.ui.home.ConnectionMethod
import svoboda.runet.abonent0007.ui.home.NeonSwitch
import svoboda.runet.abonent0007.ui.home.TileRow
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary
import svoboda.runet.abonent0007.warp.WarpGenerator
import java.util.UUID

/**
 * Подменю тонких настроек метода подключения.
 * У каждого метода — свой набор параметров и свой генератор подключения:
 *  - AWG: генерация .conf (клиентские ключи Curve25519) + серверы;
 *  - WARP: перерегистрация identity через Cloudflare API;
 *  - DPI: каталог стратегий BDP + сборщик стратегии ciadpi;
 *  - AUTO: политика предпочтения и резервного переключения.
 */
@Composable
fun MethodSettingsDialog(
    method: ConnectionMethod,
    mtu: Int,
    onMtu: (Int) -> Unit,
    onOpenServers: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0C121A),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Настройки · ${method.label}",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary,
                        )
                    }
                }
                Text(
                    text = when (method) {
                        ConnectionMethod.AWG -> "Свои серверы AmneziaWG и генератор конфигурации"
                        ConnectionMethod.WARP -> "Cloudflare WARP: регистрация и параметры"
                        ConnectionMethod.DPI_DIRECT -> "Локальный обход DPI (движок ещё не подключён)"
                        ConnectionMethod.AUTO -> "Автоматический выбор транспорта (в разработке)"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                when (method) {
                    ConnectionMethod.AWG -> AwgSettings(mtu = mtu, onMtu = onMtu, onOpenServers = onOpenServers)
                    ConnectionMethod.WARP -> WarpSettings(mtu = mtu, onMtu = onMtu)
                    ConnectionMethod.DPI_DIRECT -> DpiSettings()
                    ConnectionMethod.AUTO -> AutoSettings()
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ЗАКРЫТЬ", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

/* ---------------- AWG ---------------- */

@Composable
private fun AwgSettings(mtu: Int, onMtu: (Int) -> Unit, onOpenServers: () -> Unit) {
    var showGenerator by remember { mutableStateOf(false) }
    SectionTitle("СЕРВЕРЫ")
    TileRow(
        title = "Мои серверы AmneziaWG",
        value = "выбрать существующий или импортировать",
        opaque = true,
        onClick = onOpenServers,
    )
    TileRow(
        title = "Генератор AWG-конфига",
        value = "создать .conf с новыми ключами",
        opaque = true,
        onClick = { showGenerator = true },
    )
    SectionTitle("ПАРАМЕТРЫ ТУННЕЛЯ")
    MtuRow(mtu = mtu, onMtu = onMtu)

    if (showGenerator) {
        AwgGeneratorDialog(onDismiss = { showGenerator = false })
    }
}

@Composable
private fun MtuRow(mtu: Int, onMtu: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Elevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MTU", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("$mtu", color = TextSecondary, fontSize = 12.sp)
            }
            Slider(
                value = mtu.toFloat(),
                onValueChange = { onMtu(it.toInt().coerceIn(1200, 1480)) },
                valueRange = 1200f..1480f,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1200", color = TextSecondary, fontSize = 10.sp)
                Text("1480", color = TextSecondary, fontSize = 10.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1380, 1400, 1420, 1440, 1460, 1480).forEach { v ->
                    val selected = mtu == v
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onMtu(v) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) AccentGreen.copy(alpha = 0.18f) else Elevated.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AccentGreen else Border),
                    ) {
                        Text(
                            text = v.toString(),
                            color = if (selected) TextPrimary else TextSecondary,
                            fontSize = 9.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AwgGeneratorDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Мой сервер") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("51820") }
    var serverPub by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("10.8.0.2/32") }
    var dns by remember { mutableStateOf("1.1.1.1") }
    var status by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Генератор AWG-конфига") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host / IP сервера") }, singleLine = true)
                OutlinedTextField(port, { port = it }, label = { Text("Порт") }, singleLine = true)
                OutlinedTextField(serverPub, { serverPub = it }, label = { Text("PublicKey сервера") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Адрес клиента (CIDR)") }, singleLine = true)
                OutlinedTextField(dns, { dns = it }, label = { Text("DNS") }, singleLine = true)
                Text("Ключи клиента создаются на устройстве и не покидают его.", color = TextSecondary, fontSize = 11.sp)
                status?.let { Text(it, color = TextPrimary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = port.toIntOrNull()
                if (host.isBlank() || serverPub.isBlank() || p == null) {
                    status = "Заполните host, порт и PublicKey сервера"
                    return@TextButton
                }
                runCatching {
                    val kp = org.amnezia.awg.crypto.KeyPair()
                    val privateKey = kp.privateKey.toBase64()
                    val publicKey = kp.publicKey.toBase64()
                    val config = buildString {
                        appendLine("[Interface]")
                        appendLine("PrivateKey = $privateKey")
                        appendLine("Address = $address")
                        if (dns.isNotBlank()) appendLine("DNS = $dns")
                        appendLine("MTU = ${AppGraph.prefs.tunnelMtu}")
                        appendLine()
                        appendLine("[Peer]")
                        appendLine("PublicKey = $serverPub")
                        appendLine("Endpoint = $host:$p")
                        appendLine("AllowedIPs = 0.0.0.0/0")
                        appendLine("PersistentKeepalive = 25")
                    }
                    AppGraph.servers.add(
                        OwnServer(
                            id = UUID.randomUUID().toString(),
                            countryName = name.ifBlank { host },
                            endpointHost = host,
                            endpointPort = p,
                            config = config,
                            addedAt = System.currentTimeMillis(),
                        ),
                    )
                    status = "Создан профиль. Публичный ключ клиента:\n$publicKey"
                }.onFailure { status = "Ошибка: ${it.message}" }
            }) { Text("СОЗДАТЬ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ОТМЕНА") } },
    )
}

/* ---------------- WARP ---------------- */

@Composable
private fun WarpSettings(mtu: Int, onMtu: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SectionTitle("РЕГИСТРАЦИЯ")
    TileRow(
        title = "Новая WARP-регистрация",
        value = if (busy) "запрос к Cloudflare…" else "создать новый identity и endpoint",
        opaque = true,
        onClick = {
            if (busy) return@TileRow
            busy = true
            status = null
            scope.launch {
                val id = WarpGenerator.register()
                busy = false
                status = if (id == null) {
                    "Не удалось зарегистрироваться (сеть/API). Попробуйте позже."
                } else {
                    "Готово: endpoint ${id.endpointV4}:${id.endpointPort}, IP ${id.ipv4}"
                }
            }
        },
    )
    status?.let {
        TileRow(title = "Результат", value = it, opaque = true)
    }
    SectionTitle("ПАРАМЕТРЫ ТУННЕЛЯ")
    MtuRow(mtu = mtu, onMtu = onMtu)
}

/* ---------------- DPI ---------------- */

private data class DpiStrategy(val id: String, val name: String, val args: String)

private val DPI_STRATEGIES = listOf(
    DpiStrategy("BDP-00", "Контрольная (оригинал)", "-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An"),
    DpiStrategy("BDP-01", "Разреженная", "-Ku -a3 -An -Kt,h -d1 -s0+s -d5+s -s11+s -d18+s -s27+s -d37+s -An"),
    DpiStrategy("BDP-02", "Плотная начальная", "-Ku -a3 -An -Kt,h -d1 -s0+s -d2+s -s4+s -d6+s -s9+s -d13+s -s18+s -d24+s -s31+s -An"),
    DpiStrategy("BDP-03", "Split first", "-Ku -a3 -An -Kt,h -s0+s -d2+s -s5+s -d8+s -s12+s -d17+s -s23+s -d30+s -An"),
    DpiStrategy("BDP-04", "Disorder dominant", "-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -d5+s -d8+s -d12+s -d17+s -s23+s -d30+s -An"),
    DpiStrategy("BDP-05", "Split dominant", "-Ku -a3 -An -Kt,h -d1 -s0+s -s3+s -d6+s -s9+s -s13+s -d18+s -s24+s -s31+s -An"),
    DpiStrategy("BDP-06", "Только split", "-Ku -a3 -An -Kt,h -s0+s -s2+s -s5+s -s9+s -s14+s -s20+s -s27+s -s35+s -An"),
    DpiStrategy("BDP-07", "Только disorder", "-Ku -a3 -An -Kt,h -d1 -d3+s -d6+s -d10+s -d15+s -d21+s -d28+s -d36+s -An"),
    DpiStrategy("BDP-08", "Позднее SNI", "-Ku -a3 -An -Kt,h -d1 -s0+s -d6+s -s13+s -d21+s -s30+s -d40+s -An"),
    DpiStrategy("BDP-09", "Альтернативная", "-Ku -a3 -An -Kt,h -d1 -s1+s -d4+s -s8+s -d13+s -s19+s -d26+s -s34+s -An"),
    DpiStrategy("BDP-10", "UDP low", "-Ku -a1 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An"),
    DpiStrategy("BDP-11", "UDP high", "-Ku -a4 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An"),
    DpiStrategy("BDP-12", "TLS и HTTP раздельно", "-Ku -a3 -An -Kt -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An -Kh -d1 -s0+h -d3+h -s6+h -d9+h -s12+h -d15+h -s20+h -d25+h -s30+h -d35+h -An"),
)

@Composable
private fun DpiSettings() {
    val prefs = AppGraph.prefs
    var strategyId by remember { mutableStateOf(prefs.dpiStrategyId) }
    var port by remember { mutableStateOf(prefs.dpiSocksPort.toString()) }
    var splitCount by remember { mutableStateOf(8) }
    var disorder by remember { mutableStateOf(true) }
    var fakeCount by remember { mutableStateOf(3) }
    var generated by remember { mutableStateOf(prefs.dpiCustomStrategy) }

    SectionTitle("ДВИЖОК")
    TileRow(
        title = "ciadpi + hev-socks5-tunnel",
        value = "не подключён — в разработке",
        opaque = true,
    )
    TileRow(
        title = "SOCKS5 порт",
        value = port,
        opaque = true,
        onClick = { },
    )
    OutlinedTextField(
        value = port,
        onValueChange = {
            port = it.filter { ch -> ch.isDigit() }.take(5)
            port.toIntOrNull()?.let { p -> if (p in 1..65535) prefs.dpiSocksPort = p }
        },
        label = { Text("Порт локального SOCKS5") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    SectionTitle("СТРАТЕГИЯ")
    DPI_STRATEGIES.forEach { s ->
        val selected = s.id == strategyId
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    strategyId = s.id
                    prefs.dpiStrategyId = s.id
                },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) AccentGreen.copy(alpha = 0.14f) else Elevated.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AccentGreen else Border),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("${s.id} · ${s.name}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (selected) {
                    Text(s.args, color = TextSecondary, fontSize = 10.sp)
                }
            }
        }
    }

    SectionTitle("ГЕНЕРАТОР СТРАТЕГИИ")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Точек split: $splitCount", color = TextSecondary, fontSize = 11.sp)
        Slider(
            value = splitCount.toFloat(),
            onValueChange = { splitCount = it.toInt().coerceIn(3, 10) },
            valueRange = 3f..10f,
        )
        Text("UDP fake: $fakeCount", color = TextSecondary, fontSize = 11.sp)
        Slider(
            value = fakeCount.toFloat(),
            onValueChange = { fakeCount = it.toInt().coerceIn(1, 4) },
            valueRange = 1f..4f,
        )
        TileRow(
            title = "Disorder",
            value = if (disorder) "включён" else "выключен",
            opaque = true,
            control = { NeonSwitch(disorder, { disorder = it }) },
        )
        TileRow(
            title = "Собрать стратегию",
            value = "сформировать аргументы ciadpi",
            opaque = true,
            onClick = {
                val step = 38 / splitCount.coerceAtLeast(1)
                val points = (0 until splitCount).map { it * step }.sorted()
                val sb = StringBuilder("-Ku -a$fakeCount -An -Kt,h")
                points.forEachIndexed { i, pos ->
                    sb.append(' ')
                    if (disorder && i % 2 == 1) sb.append("-d$pos+s") else sb.append("-s$pos+s")
                }
                sb.append(" -An")
                generated = sb.toString()
                prefs.dpiCustomStrategy = generated
            },
        )
        if (generated.isNotBlank()) {
            TileRow(title = "Сгенерировано", value = generated, opaque = true)
        }
    }
}

/* ---------------- AUTO ---------------- */

@Composable
private fun AutoSettings() {
    val prefs = AppGraph.prefs
    var preferWarp by remember { mutableStateOf(prefs.autoPreferWarp) }
    var fallback by remember { mutableStateOf(prefs.autoFallback) }

    SectionTitle("ПОЛИТИКА")
    TileRow(
        title = "Предпочтительный транспорт",
        value = if (preferWarp) "WARP" else "AWG",
        opaque = true,
        onClick = {
            preferWarp = !preferWarp
            prefs.autoPreferWarp = preferWarp
        },
    )
    TileRow(
        title = "Резервное переключение",
        value = if (fallback) "включено" else "выключено",
        opaque = true,
        control = {
            NeonSwitch(fallback) {
                fallback = it
                prefs.autoFallback = it
            }
        },
    )
    TileRow(
        title = "Порядок",
        value = if (preferWarp) "WARP → AWG" else "AWG → WARP",
        opaque = true,
    )
    Text(
        "AUTO включится после стабилизации DPI-движка: сначала проверяется предпочтительный транспорт, затем резервный.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
}
