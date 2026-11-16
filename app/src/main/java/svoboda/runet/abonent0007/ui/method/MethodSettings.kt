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
import androidx.compose.foundation.horizontalScroll
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
import svoboda.runet.abonent0007.dpi.DPI_CATALOG_SECTIONS
import svoboda.runet.abonent0007.dpi.dpiStrategyById
import svoboda.runet.abonent0007.warp.SeedCatalog
import svoboda.runet.abonent0007.split.ServiceCatalog
import svoboda.runet.abonent0007.warp.WarpProfileFactory
import svoboda.runet.abonent0007.ui.benchmark.DpiBenchmarkDialog
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
    var showBenchmark by remember { mutableStateOf(false) }
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
                        ConnectionMethod.DPI_DIRECT -> "Локальный обход DPI (ciadpi + tun2socks)"
                        ConnectionMethod.AUTO -> "Автоматический выбор транспорта (в разработке)"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                when (method) {
                    ConnectionMethod.AWG -> AwgSettings(mtu = mtu, onMtu = onMtu, onOpenServers = onOpenServers)
                    ConnectionMethod.WARP -> WarpSettings(mtu = mtu, onMtu = onMtu)
                    ConnectionMethod.DPI_DIRECT -> DpiSettings(onOpenBenchmark = { showBenchmark = true })
                    ConnectionMethod.AUTO -> AutoSettings()
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ЗАКРЫТЬ", color = TextSecondary)
                }

                if (showBenchmark) {
                    DpiBenchmarkDialog(onDismiss = { showBenchmark = false })
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

    ServiceScopePicker(title = "СЕРВИСЫ В ТУННЕЛЬ (WARP)")

    SectionTitle("DNS В КОНФИГЕ")
    var dnsValue by remember { mutableStateOf(WarpGenerator.resolveDns("cloudflare")) }
    Text(
        text = "Какой резолвер писать в сгенерированные конфиги. Доменные антиблок-резолверы " +
            "разрешаются в IP при создании профиля — AmneziaWG принимает только адреса.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    DnsResolverPanel(
        current = dnsValue,
        onApply = { dnsValue = it },
    )

    SectionTitle("СЕРВИСЫ НАПРЯМУЮ")
    var directServices by remember { mutableStateOf(AppGraph.prefs.serviceDirectSelection) }
    Text(
        text = "Раздельное туннелирование: выбранные приложения идут мимо туннеля (мимо WARP). " +
            "Пакеты определяются по названию установленного приложения. LAN-трафик при этом " +
            "и так идёт напрямую — физические подсети приоритетнее маршрута 0/0 туннеля.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    ServiceCatalog.services.chunked(2).forEach { rowServices ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rowServices.forEach { service ->
                val selected = directServices.contains(service.id)
                TextButton(onClick = {
                    directServices = if (selected) directServices - service.id else directServices + service.id
                    AppGraph.prefs.serviceDirectSelection = directServices
                }) {
                    Text(
                        text = service.title,
                        fontSize = 12.sp,
                        color = if (selected) AccentGreen else TextSecondary,
                    )
                }
            }
        }
    }

    SectionTitle("WARP-ПРОФИЛИ")
    var profileCount by remember { mutableStateOf(10) }
    var profileBusy by remember { mutableStateOf(false) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf(AppGraph.manager.generatedProfiles()) }

    Text(
        text = "Массовое создание конфигов: одна регистрация в Cloudflare, разные anycast-endpoint'ы " +
            "и рандомизированные junk-параметры (Jc/Jmin/Jmax) — профили не выглядят одинаково для DPI.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(5, 10, 25).forEach { n ->
            TextButton(onClick = { profileCount = n }) {
                Text(
                    text = "$n",
                    color = if (profileCount == n) AccentGreen else TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
    TileRow(
        title = "Создать профили WARP",
        value = if (profileBusy) "регистрация и сборка…" else "новая identity + $profileCount конфигов",
        opaque = true,
        onClick = {
            if (profileBusy) return@TileRow
            profileBusy = true
            profileStatus = null
            scope.launch {
                val seedRaw = SeedCatalog.defaultSeed(AppGraph.seeds)?.raw_config.orEmpty()
                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                    dns = dnsValue,
                )
                result.profiles.forEach { AppGraph.manager.addGeneratedProfile(it) }
                profiles = AppGraph.manager.generatedProfiles()
                profileBusy = false
                profileStatus = if (result.profiles.isEmpty()) {
                    "Не удалось создать профили. ${result.note}"
                } else {
                    result.note
                }
            }
        },
    )
    profileStatus?.let {
        TileRow(title = "Результат", value = it, opaque = true)
    }
    profiles.forEach { profile ->
        val jc = Regex("(?m)^Jc\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        val jmin = Regex("(?m)^Jmin\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        val jmax = Regex("(?m)^Jmax\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${profile.host}:${profile.port}" +
                    (if (jc.isNotBlank()) "   ·   Jc $jc, Jmin $jmin, Jmax $jmax" else "") +
                    (if (profile.observedCountry.isNotBlank()) "   ·   ${profile.observedCountry}" else ""),
                color = TextPrimary,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        profileStatus = "Подключение ${profile.host}:${profile.port} …"
                        val ok = AppGraph.manager.connectGeneratedProfile(profile)
                        profileStatus = if (ok) {
                            "Подключено: ${profile.host}:${profile.port}"
                        } else {
                            "Не удалось подключить ${profile.host}:${profile.port}"
                        }
                    }
                }) { Text("ПОДКЛЮЧИТЬ", fontSize = 12.sp, color = AccentGreen) }
                TextButton(onClick = {
                    AppGraph.manager.removeGeneratedProfile(profile)
                    profiles = AppGraph.manager.generatedProfiles()
                }) { Text("УДАЛИТЬ", fontSize = 12.sp) }
            }
        }
    }
    if (profiles.isNotEmpty()) {
        TextButton(onClick = {
            AppGraph.manager.clearGeneratedProfiles()
            profiles = AppGraph.manager.generatedProfiles()
        }) { Text("УДАЛИТЬ ВСЕ ПРОФИЛИ", fontSize = 12.sp) }
    }
}

/* ---------------- DPI ---------------- */


private val DPI_TOKEN = Regex("^-{1,2}[A-Za-z0-9?_=:]+(?:[,+./-][A-Za-z0-9?_=:]+)*$")

/** Лексическая проверка argv для ciadpi. Пустая строка = "использовать каталог" (валидное состояние). */
private fun validateDpiArgs(args: String): String? {
    val t = args.trim()
    if (t.isEmpty()) return null
    if (t.length > 1024) return "Слишком длинная строка (макс. 1024 символа)"
    val tokens = t.split(Regex("\\s+"))
    if (tokens.size > 64) return "Слишком много аргументов (макс. 64)"
    val bad = tokens.firstOrNull { !DPI_TOKEN.matches(it) }
    return if (bad != null) "Недопустимый аргумент: $bad" else null
}

private const val DPI_COMMENT_ENGINE =
    "Весь трафик приложений уходит в TUN → tun2socks (HEV) → локальный SOCKS5 127.0.0.1:1080 → ciadpi → интернет.\nСобственный пакет приложения исключён из VPN, иначе ciadpi оказался бы в собственном туннеле (рекурсия)."

private const val DPI_COMMENT_CATALOG =
    "Каталог ByeDPI: контрольные (BDP), простые P1, fake-пакеты, комбинированные P2, расширенные (экспериментальные) и HTTP-only.\nОдна стратегия применяется ко ВСЕМУ TCP-трафику через обход. Нажмите — применится к следующему подключению. Приоритет ниже, чем у своей стратегии."

private const val DPI_COMMENT_BUILDER =
    "Быстрая сборка строки: число split-точек, доля disorder, UDP fake-пакеты.\nКнопка «Собрать стратегию» сохраняет результат как пользовательскую строку (каталог отключается)."

private const val DPI_COMMENT_CUSTOM =
    "Пустое поле = стратегия из каталога.\nФормат: токены через пробел, например: -Ku -a3 -An -Kt,h -d1 -s0+s -An — любые опции ByeDPI.\nСтроку получает ciadpi напрямую, без shell. Проверяйте счётчик «argv: N аргументов»."

private const val DPI_COMMENT_LIMITS =
    "Прямой доступ к DC Telegram (149.154.x / 91.108.x) блокируется на IP-уровне — ByeDPI это не обходит.\nКлиент Telegram на этих IP не подключится ни через одну стратегию (доступна веб-версия и web.telegram.org).\nYouTube на этой сети сбрасывается по SNI — нужен AWG или WARP."

@Composable
private fun DpiComment(text: String) {
    Text(text = text, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
}

@Composable
private fun DpiCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Elevated.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DpiSettings(onOpenBenchmark: () -> Unit = {}) {
    val prefs = AppGraph.prefs
    var strategyId by remember { mutableStateOf(prefs.dpiStrategyId) }
    var port by remember { mutableStateOf(prefs.dpiSocksPort.toString()) }
    var splitCount by remember { mutableStateOf(8) }
    var disorder by remember { mutableStateOf(true) }
    var fakeCount by remember { mutableStateOf(3) }
    var generated by remember { mutableStateOf(prefs.dpiCustomStrategy) }
    var customArgs by remember { mutableStateOf(prefs.dpiCustomStrategy) }
    var customError by remember { mutableStateOf<String?>(null) }
    var customSaved by remember { mutableStateOf(false) }
    var mapDns by remember { mutableStateOf(prefs.dpiMapDns) }

    SectionTitle("ДВИЖОК")
    DpiCard {
        TileRow(
            title = "ciadpi + hev-socks5-tunnel",
            value = "локальный SOCKS5 · MTProto-DC напрямую недоступны",
            opaque = true,
        )
        DpiComment(DPI_COMMENT_ENGINE)
        OutlinedTextField(
            value = port,
            onValueChange = {
                port = it.filter { ch -> ch.isDigit() }.take(5)
                port.toIntOrNull()?.let { p -> if (p in 1..65535) prefs.dpiSocksPort = p }
            },
            label = { Text("Порт локального SOCKS5 (по умолчанию 1080)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TileRow(
            title = "DNS через туннель (mapdns)",
            value = if (mapDns) "включён" else "выключен",
            opaque = true,
            control = { NeonSwitch(mapDns, { mapDns = it; prefs.dpiMapDns = it }) },
        )
        DpiComment("Домены резолвятся внутри обхода (198.18.0.2), соединения идут по домену через ciadpi.\nНе зависит от DNS провайдера и подмены. Не обходит IP-блокировку Telegram.\nЭкспериментально: применится при следующем подключении.")
        TileRow(
            title = "Тестирование стратегий",
            value = "бенчмарк: сравнить стратегии по доменам",
            opaque = true,
            onClick = onOpenBenchmark,
        )
        DpiComment("Бенчмарк по очереди перезапускает ciadpi с каждой стратегией каталога, проверяет выбранные домены (DNS/TCP/TLS/HTTP) и строит рейтинг. Исходные настройки стратегии восстанавливаются после прогона.")
    }

    ServiceScopePicker(title = "СЕРВИСЫ В ТУННЕЛЬ (DPI)")

    SectionTitle("СТРАТЕГИИ")
    DpiComment(DPI_COMMENT_CATALOG)
    DPI_CATALOG_SECTIONS.forEach { (section, strategies) ->
        Text(
            text = section,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
        strategies.forEach { s ->
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
    }

    SectionTitle("КОНСТРУКТОР")
    DpiCard {
        DpiComment(DPI_COMMENT_BUILDER)
        Text("Число split: $splitCount", color = TextSecondary, fontSize = 11.sp)
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
                customArgs = generated
                customError = null
                customSaved = true
            },
        )
        if (generated.isNotBlank()) {
            TileRow(title = "Строка стратегии", value = generated, opaque = true)
        }
    }

    SectionTitle("СВОЯ СТРАТЕГИЯ")
    if (prefs.dpiCustomStrategy.isNotBlank()) {
        TileRow(
            title = "Своя стратегия активна",
            value = "имеет приоритет над каталогом",
            opaque = true,
        )
    }
    DpiCard {
        OutlinedTextField(
            value = customArgs,
            onValueChange = { v ->
                customArgs = v
                customError = validateDpiArgs(v)
                customSaved = false
            },
            label = { Text("Аргументы ciadpi / пользовательская стратегия") },
            placeholder = { Text("-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -An") },
            singleLine = false,
            minLines = 2,
            maxLines = 6,
            isError = customError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        DpiComment(DPI_COMMENT_CUSTOM)
        customError?.let { err ->
            Text(err, color = Color(0xFFFFB300), fontSize = 11.sp)
        }
        if (customError == null && customArgs.trim().isNotEmpty()) {
            Text(
                text = "argv: " + customArgs.trim().split(Regex("\\s+")).size + " аргументов · готово к сохранению",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                customArgs = dpiStrategyById(strategyId)?.args.orEmpty()
                customError = validateDpiArgs(customArgs)
                customSaved = false
            }) { Text("Копировать из каталога") }
            TextButton(
                enabled = customError == null,
                onClick = {
                    prefs.dpiCustomStrategy = customArgs.trim()
                    customSaved = true
                },
            ) { Text("Сохранить") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                customArgs = ""
                prefs.dpiCustomStrategy = ""
                customError = null
                customSaved = false
            }) { Text("Очистить") }
        }
        if (customSaved) {
            Text(
                text = "Сохранено. Переподключитесь, чтобы применить (связь по SOCKS5 будет проверена автоматически).",
                color = AccentGreen,
                fontSize = 11.sp,
            )
        }
    }
    DpiComment(DPI_COMMENT_LIMITS)
}


/* ---------------- AUTO ---------------- */

@Composable
private fun AutoSettings() {
    val prefs = AppGraph.prefs
    var transportOrder by remember { mutableStateOf(prefs.autoTransportOrder) }
    var countryPriority by remember { mutableStateOf(prefs.autoCountryPriority) }
    var strict by remember { mutableStateOf(prefs.autoCountryStrict) }
    var holdMinutes by remember { mutableStateOf(prefs.autoHoldMinutes.toString()) }

    SectionTitle("AUTO · АВТОМАТИЧЕСКИЙ ПОДБОР")
    Text(
        text = "AUTO выбирает один транспорт по правилам и держит его. Переключение по " +
            "foreground-приложению НЕ делается — это рвёт активные сессии (загрузка файла, " +
            "передача в Telegram). При падении туннеля — ограниченный переход на следующего кандидата.",
        color = TextSecondary,
        fontSize = 11.sp,
    )

    Text(
        text = "Порядок транспортов",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("AWG" to "AWG", "WARP" to "WARP", "DPI" to "DPI").forEach { (id, title) ->
            val active = transportOrder.split(',').any { it.trim().uppercase() == id }
            TextButton(onClick = {
                val current = transportOrder.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val next = if (active) current - id else current + id
                transportOrder = next.joinToString(",")
                prefs.autoTransportOrder = transportOrder
            }) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = if (active) AccentGreen else TextSecondary,
                )
            }
        }
    }

    Text(
        text = "Приоритет стран выхода",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("NL", "FI", "US").forEach { code ->
            val active = countryPriority.split(',').any { it.trim().uppercase() == code }
            TextButton(onClick = {
                val current = countryPriority.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val next = if (active) current - code else current + code
                countryPriority = next.joinToString(",")
                prefs.autoCountryPriority = countryPriority
            }) {
                Text(
                    text = code,
                    fontSize = 12.sp,
                    color = if (active) AccentGreen else TextSecondary,
                )
            }
        }
    }

    TileRow(
        title = "Строгая страна",
        value = if (strict) "не уходить в другие страны" else "разрешены другие страны",
        opaque = true,
        control = {
            NeonSwitch(strict) {
                strict = it
                prefs.autoCountryStrict = it
            }
        },
    )
    OutlinedTextField(
        value = holdMinutes,
        onValueChange = {
            holdMinutes = it.filter { ch -> ch.isDigit() }.take(3)
            holdMinutes.toIntOrNull()?.let { prefs.autoHoldMinutes = it }
        },
        label = { Text("Удерживать транспорт, мин (антидребезг)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Страна WARP = ближайший PoP Cloudflare и не гарантируется: у профилей измерение " +
            "фактической страны (после подключения), у своих AWG-серверов страна задаётся вами. " +
            "Для строгой гарантии NL/FI/US нужны собственные серверы в этих странах.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
}
