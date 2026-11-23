package svoboda.runet.abonent0007.ui.awg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.awg.AwgGenerator
import svoboda.runet.abonent0007.awg.AwgPreset
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.ui.method.DnsResolverPanel
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary
import svoboda.runet.abonent0007.warp.WarpGenerator
import java.util.UUID

/**
 * Автогенерация AWG-профиля: identity с relay obnova-warp → сервер страны →
 * 3 предустановки маскировки. Создаёт профиль сервера и подключается.
 */
@Composable
fun AwgGeneratorDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf(AwgGenerator.COUNTRIES.first()) }
    var preset by remember { mutableStateOf(AwgPreset.V1) }
    var dns by remember { mutableStateOf(AppGraph.prefs.dnsServers) }
    var mtu by remember { mutableStateOf(AppGraph.prefs.tunnelMtu) }
    var ipv6 by remember { mutableStateOf(AppGraph.prefs.ipv6Enabled) }
    var keepalive by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Автогенерация AWG") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Identity берётся с вашего relay obnova-warp. Параметры: Jc 4 / Jmin 40 / Jmax 70, " +
                        "S1..S4 = 0, H1..H4 = 1..4.",
                    color = TextSecondary, fontSize = 11.sp,
                )

                SectionTitle("СЕРВЕР")
                AwgChipRow(
                    items = AwgGenerator.COUNTRIES.map { it.title },
                    selected = country.title,
                    onSelect = { title -> AwgGenerator.COUNTRIES.firstOrNull { it.title == title }?.let { country = it } },
                )
                if (country.needsRegistration) {
                    Text(
                        "Внимание: нестандартные серверы требуют регистрации в Telegram-боте llimonix.",
                        color = Color(0xFFFFB300), fontSize = 11.sp,
                    )
                }

                SectionTitle("ПРЕДУСТАНОВКА")
                AwgChipRow(
                    items = AwgPreset.entries.map { it.label },
                    selected = preset.label,
                    onSelect = { name -> AwgPreset.entries.firstOrNull { it.label == name }?.let { preset = it } },
                )
                Text(preset.description, color = TextSecondary, fontSize = 11.sp)

                SectionTitle("DNS")
                DnsResolverPanel(current = dns, onApply = { dns = it })

                SectionTitle("ПАРАМЕТРЫ")
                OutlinedTextField(
                    value = mtu.toString(),
                    onValueChange = { v -> v.filter { it.isDigit() }.take(4).toIntOrNull()?.let { mtu = it.coerceIn(1200, 1480) } },
                    label = { Text("MTU (1200–1480)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keepalive,
                    onValueChange = { keepalive = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("PersistentKeepalive (0 — выкл)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("IPv6", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = ipv6, onCheckedChange = { ipv6 = it })
                }

                status?.let { Text(it, color = TextPrimary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        status = "Получаем identity с relay…"
                        val identity = WarpGenerator.fetchIdentity()
                        if (identity == null) {
                            status = "Relay недоступен — попробуйте позже"
                            busy = false
                            return@launch
                        }
                        val ka = keepalive.trim().toIntOrNull()?.takeIf { it > 0 }
                        val config = AwgGenerator.buildConfig(identity, country, preset, dns, mtu, ipv6, ka)
                        val (host, port) = AwgGenerator.randomEndpoint(country)
                        val server = OwnServer(
                            id = UUID.randomUUID().toString(),
                            countryName = country.title,
                            countryCode = country.iso.ifBlank { country.id },
                            endpointHost = host,
                            endpointPort = port,
                            config = config,
                            addedAt = System.currentTimeMillis(),
                        )
                        AppGraph.servers.add(server)
                        status = "Профиль создан: ${country.title} · ${preset.label}. Подключение…"
                        val ok = AppGraph.manager.selectServer(server)
                        status = if (ok) "Подключено: ${country.title} · ${preset.name}" else "Профиль создан, но подключение не удалось"
                        busy = false
                    }
                },
            ) { Text(if (busy) "РАБОТАЮ…" else "СГЕНЕРИРОВАТЬ") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ЗАКРЫТЬ") }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun AwgChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    val isSel = item == selected
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .let { m -> m },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) AccentGreen.copy(alpha = 0.18f) else Elevated.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSel) AccentGreen else Border),
                        onClick = { onSelect(item) },
                    ) {
                        Text(
                            text = item,
                            color = if (isSel) TextPrimary else TextSecondary,
                            fontSize = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
