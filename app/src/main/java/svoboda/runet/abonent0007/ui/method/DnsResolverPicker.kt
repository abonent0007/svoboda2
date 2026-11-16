package svoboda.runet.abonent0007.ui.method

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.net.DnsResolvers
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.ErrorRed
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary

/**
 * Выбор DNS-резолвера с проверкой валидности и вводом своего адреса.
 *
 * Проверка — реальный DNS-запрос к резолверу: «✓ 42 мс» / «✗ нет ответа».
 * Антиблок-резолверы помечены — они требуют, чтобы весь трафик шёл через туннель.
 */
@Composable
fun DnsResolverPanel(
    current: String,
    onApply: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val checks = remember { mutableStateMapOf<String, DnsResolvers.Check>() }
    val checking = remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf(current) }
    var customCheck by remember { mutableStateOf<DnsResolvers.Check?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Проверка — настоящий DNS-запрос к резолверу.",
            color = TextSecondary,
            fontSize = 11.sp,
        )
        DnsResolvers.catalog.forEach { def ->
            val check = checks[def.id]
            val isChecking = checking.value == def.id
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = def.title + (if (def.community) " · антиблок" else ""),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = def.host + (def.note?.let { " · $it" } ?: ""),
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                    when {
                        isChecking -> Text("проверка…", color = TextSecondary, fontSize = 10.sp)
                        check == null -> Unit
                        check.ok -> Text(
                            text = "✓ отвечает" + (check.latencyMs?.let { " · ${it} мс" } ?: "") +
                                (check.ips.firstOrNull()?.let { " · $it" } ?: ""),
                            color = AccentGreen,
                            fontSize = 10.sp,
                        )
                        else -> Text(
                            text = "✗ " + (check.error ?: "не отвечает"),
                            color = ErrorRed,
                            fontSize = 10.sp,
                        )
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        checking.value = def.id
                        checks[def.id] = DnsResolvers.check(def)
                        checking.value = null
                    }
                }) { Text("ПРОВЕРИТЬ", fontSize = 11.sp) }
                TextButton(onClick = {
                    scope.launch {
                        val ips = DnsResolvers.runCatchingAddresses(def)
                        if (ips.isNotBlank()) onApply(ips)
                    }
                }) { Text("ВЫБРАТЬ", fontSize = 11.sp, color = AccentGreen) }
            }
        }

        Text(
            text = "СВОЙ DNS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedTextField(
            value = custom,
            onValueChange = {
                custom = it
                customCheck = null
            },
            label = { Text("Свой резолвер (IP или домен)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        customCheck?.let { check ->
            Text(
                text = if (check.ok) {
                    "✓ отвечает" + (check.latencyMs?.let { " · ${it} мс" } ?: "")
                } else {
                    "✗ " + (check.error ?: "не отвечает")
                },
                color = if (check.ok) AccentGreen else ErrorRed,
                fontSize = 10.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                scope.launch {
                    customCheck = DnsResolvers.checkCustom(custom)
                }
            }) { Text("ПРОВЕРИТЬ", fontSize = 11.sp) }
            TextButton(onClick = {
                scope.launch {
                    val ips = DnsResolvers.runCatchingAddressesCustom(custom)
                    if (ips.isNotEmpty()) onApply(ips)
                }
            }) { Text("ПРИМЕНИТЬ", fontSize = 11.sp, color = AccentGreen) }
        }
        Text(
            text = "Антиблок-резолверы возвращают адреса, рассчитанные на обход блокировок: " +
                "при них весь трафик должен идти через туннель (раздельное туннелирование отключите).",
            color = TextSecondary,
            fontSize = 10.sp,
        )
    }
}

/** Список адресов резолвера строкой (для записи в настройки). */
private suspend fun DnsResolvers.runCatchingAddresses(def: DnsResolvers.Def): String =
    addressesOf(def).joinToString(", ").ifBlank { def.host }

private suspend fun DnsResolvers.runCatchingAddressesCustom(value: String): String =
    addressesOfCustom(value).joinToString(", ")
