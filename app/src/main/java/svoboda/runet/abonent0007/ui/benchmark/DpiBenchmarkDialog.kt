package svoboda.runet.abonent0007.ui.benchmark

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.dpi.benchmark.DpiBenchmarkState
import svoboda.runet.abonent0007.dpi.benchmark.DpiDomainCatalog
import svoboda.runet.abonent0007.dpi.benchmark.DpiStrategyRanker
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.ErrorRed
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary
import svoboda.runet.abonent0007.vpn.VpnPhase
import kotlinx.coroutines.launch

/**
 * Экран бенчмарка DPI-стратегий.
 *
 * Три состояния: выбор доменов → прогресс прогона → итоговый рейтинг.
 */
@Composable
fun DpiBenchmarkDialog(onDismiss: () -> Unit) {
    val state by AppGraph.dpiBenchmark.state.collectAsState()
    val prefs = AppGraph.prefs
    val context = LocalContext.current
    var selected by remember { mutableStateOf(prefs.dpiBenchmarkDomains) }
    var exportNote by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C121A)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ТЕСТИРОВАНИЕ DPI",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Закрыть",
                            tint = TextPrimary,
                        )
                    }
                }

                when (val current = state) {
                    is DpiBenchmarkState.Running -> RunView(current)
                    is DpiBenchmarkState.Paused -> RunView(current.running)
                    is DpiBenchmarkState.Preparing -> {
                        Text(current.message, color = TextSecondary, fontSize = 12.sp)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is DpiBenchmarkState.Finished -> ResultsView(
                        state = current,
                        onApply = { strategyId ->
                            AppGraph.appScope.launch {
                                val ok = AppGraph.dpiBenchmark.applyStrategy(strategyId)
                                exportNote = if (ok) {
                                    "Стратегия применена и ciadpi перезапущен"
                                } else {
                                    "Не удалось применить стратегию"
                                }
                            }
                        },
                        onExport = { exportNote = exportResults(context, current) },
                    )
                    is DpiBenchmarkState.Failed -> {
                        Text(current.reason, color = ErrorRed, fontSize = 12.sp)
                        TextButton(onClick = { AppGraph.dpiBenchmark.reset() }) { Text("НАЗАД К ВЫБОРУ") }
                    }
                    DpiBenchmarkState.Idle -> SelectionView(
                        selected = selected,
                        onToggle = { id ->
                            selected = if (selected.contains(id)) selected - id else selected + id
                            prefs.dpiBenchmarkDomains = selected
                        },
                        onSelectAll = {
                            selected = DpiDomainCatalog.domains.map { it.id }.toSet()
                            prefs.dpiBenchmarkDomains = selected
                        },
                        onClear = {
                            selected = emptySet()
                            prefs.dpiBenchmarkDomains = selected
                        },
                        onReset = {
                            selected = DpiDomainCatalog.defaultSelection
                            prefs.dpiBenchmarkDomains = selected
                        },
                        onStart = {
                            AppGraph.dpiBenchmark.start(selected)
                        },
                    )
                }

                exportNote?.let {
                    Text(it, color = AccentGreen, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SelectionView(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
    onStart: () -> Unit,
) {
    val dpiConnected = AppGraph.vpn.phase.value == VpnPhase.CONNECTED ||
        AppGraph.vpn.phase.value == VpnPhase.DEGRADED
    val methodIsDpi = AppGraph.prefs.connectionMethod == "DPI_DIRECT"

    Text(
        text = "Выбрано доменов: ${selected.size} из ${DpiDomainCatalog.domains.size}",
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onSelectAll) { Text("Выбрать все", fontSize = 12.sp) }
        TextButton(onClick = onClear) { Text("Снять все", fontSize = 12.sp) }
        TextButton(onClick = onReset) { Text("Сбросить", fontSize = 12.sp) }
    }

    if (!dpiConnected || !methodIsDpi) {
        Text(
            text = "Для тестирования нужен подключённый DPI-режим (способ подключения → DPI). " +
                "Сейчас: ${if (methodIsDpi) "не подключено" else "выбран другой способ"}.",
            color = ErrorRed,
            fontSize = 11.sp,
        )
    }

    DpiDomainCatalog.categories.forEach { category ->
        Text(
            text = category.title.uppercase(),
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        category.domains.forEach { domain ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(domain.id) }
                    .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = selected.contains(domain.id),
                    onCheckedChange = { onToggle(domain.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentGreen,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = Color(0xFF0C121A),
                    ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(domain.domain, color = TextPrimary, fontSize = 13.sp)
                    Text(
                        text = domain.title + (domain.note?.let { " · $it" } ?: ""),
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        onClick = onStart,
        enabled = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("НАЧАТЬ ТЕСТИРОВАНИЕ", color = if (selected.isEmpty()) TextSecondary else AccentGreen)
    }
}

@Composable
private fun RunView(running: DpiBenchmarkState.Running) {
    Text(
        text = "Стратегия: ${running.strategyIndex} / ${running.totalStrategies}",
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    Text(text = running.strategyName + " (" + running.strategyId + ")", color = TextSecondary, fontSize = 11.sp)
    LinearProgressIndicator(
        progress = { running.domainsChecked.toFloat() / running.totalDomains.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Доменов проверено: ${running.domainsChecked} / ${running.totalDomains}   ·   " +
            "Успешно: ${running.successful}   ·   Ошибки: ${running.failed}",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    running.currentDomain?.let {
        Text(text = "Текущий домен: $it", color = TextPrimary, fontSize = 12.sp)
    }
    running.currentAttempt?.let { attempt ->
        Text(
            text = "DNS: ${status(attempt.dnsOk)}${attempt.resolvedIp?.let { " ($it)" } ?: ""}\n" +
                "TCP: ${status(attempt.tcpOk)}${attempt.tcpMs?.let { " · ${it} мс" } ?: ""}\n" +
                "TLS: ${status(attempt.tlsOk)}${attempt.tlsMs?.let { " · ${it} мс" } ?: ""}\n" +
                "HTTP: ${status(attempt.httpOk)}${attempt.httpStatus?.let { " · $it" } ?: ""}" +
                (attempt.httpMs?.let { " · ${it} мс" } ?: "") +
                (attempt.errorType?.let { "\nОшибка: $it — ${attempt.errorMessage ?: ""}" } ?: ""),
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
    Text(text = "Затрачено: ${running.elapsedMs / 1000} с", color = TextSecondary, fontSize = 11.sp)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            val current = AppGraph.dpiBenchmark.state.value
            if (current is DpiBenchmarkState.Paused) AppGraph.dpiBenchmark.resume()
            else AppGraph.dpiBenchmark.pause("Пауза пользователем")
        }) { Text(if (AppGraph.dpiBenchmark.state.value is DpiBenchmarkState.Paused) "ПРОДОЛЖИТЬ" else "ПАУЗА") }
        TextButton(onClick = { AppGraph.dpiBenchmark.stop() }) {
            Text("ОСТАНОВИТЬ", color = ErrorRed)
        }
    }
}

@Composable
private fun ResultsView(
    state: DpiBenchmarkState.Finished,
    onApply: (String) -> Unit,
    onExport: () -> Unit,
) {
    val ranked = DpiStrategyRanker.rank(state.results)
    Text(
        text = "Протестировано стратегий: ${ranked.size}   ·   сессия #${state.sessionId}",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    val best = state.best
    if (best != null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AccentGreen.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ЛУЧШАЯ СТРАТЕГИЯ", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${best.strategyId} · ${best.strategyName}", color = TextPrimary, fontSize = 14.sp)
                Text(
                    text = "Доступно доменов: ${best.successfulDomains} / ${best.domains.size}   ·   " +
                        "Успех попыток: ${(best.successRate * 100).toInt()}%   ·   " +
                        "Медиана HTTP: ${best.medianHttpMs?.let { "$it мс" } ?: "—"}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(text = best.strategyArgs, color = TextSecondary, fontSize = 10.sp)
                TextButton(onClick = { onApply(best.strategyId) }) {
                    Text("ПРИМЕНИТЬ ЭТУ СТРАТЕГИЮ", color = AccentGreen)
                }
                state.appliedStrategyId?.let {
                    Text("Выбрана по результатам тестирования: $it", color = AccentGreen, fontSize = 11.sp)
                }
            }
        }
    }

    Text("РЕЙТИНГ", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    ranked.take(20).forEachIndexed { index, result ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.width(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("${result.strategyId} · ${result.strategyName}", color = TextPrimary, fontSize = 12.sp)
                Text(
                    text = "успешно ${result.successfulDomains}/${result.domains.size}   ·   " +
                        "${(result.successRate * 100).toInt()}%   ·   " +
                        "медиана ${result.medianHttpMs?.let { "$it мс" } ?: "—"}   ·   " +
                        "ошибок ${result.errorCount}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
    TextButton(onClick = onExport) { Text("ЭКСПОРТ JSON / CSV", color = AccentGreen) }
}

private fun status(ok: Boolean): String = if (ok) "OK" else "—"

/** Экспорт результатов в JSON и CSV (в filesDir/exports). */
private fun exportResults(context: android.content.Context, state: DpiBenchmarkState.Finished): String {
    return try {
        val out = java.io.File(context.filesDir, "exports")
        out.mkdirs()
        val json = java.io.File(out, "dpi_benchmark_${state.sessionId}.json")
        val csv = java.io.File(out, "dpi_benchmark_${state.sessionId}.csv")
        json.writeText(buildJson(state), Charsets.UTF_8)
        csv.writeText(buildCsv(state), Charsets.UTF_8)
        "Экспорт: ${json.absolutePath} и ${csv.absolutePath}"
    } catch (e: Exception) {
        "Ошибка экспорта: ${e.message}"
    }
}

private fun buildJson(state: DpiBenchmarkState.Finished): String {
    val sb = StringBuilder()
    sb.append("{\n  \"session_id\": ${state.sessionId},\n  \"results\": [\n")
    state.results.forEachIndexed { index, r ->
        sb.append("    {\n")
        sb.append("      \"strategy_id\": \"${r.strategyId}\",\n")
        sb.append("      \"strategy_name\": \"${r.strategyName}\",\n")
        sb.append("      \"strategy_args\": \"${r.strategyArgs}\",\n")
        sb.append("      \"successful_domains\": ${r.successfulDomains},\n")
        sb.append("      \"total_domains\": ${r.domains.size},\n")
        sb.append("      \"success_rate\": ${r.successRate},\n")
        sb.append("      \"median_http_ms\": ${r.medianHttpMs ?: "null"},\n")
        sb.append("      \"median_tls_ms\": ${r.medianTlsMs ?: "null"},\n")
        sb.append("      \"error_count\": ${r.errorCount},\n")
        sb.append("      \"domains\": [\n")
        r.domains.forEachIndexed { di, d ->
            sb.append("        {\"domain\": \"${d.domain.domain}\", \"success\": ${d.success}, ")
            sb.append("\"http_status\": ${d.attempts.firstNotNullOfOrNull { it.httpStatus } ?: "null"}, ")
            sb.append("\"http_ms\": ${d.medianHttpMs ?: "null"}, \"error\": \"${d.errorType ?: ""}\"}")
            if (di < r.domains.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("      ]\n    }")
        if (index < state.results.size - 1) sb.append(",")
        sb.append("\n")
    }
    sb.append("  ]\n}\n")
    return sb.toString()
}

private fun buildCsv(state: DpiBenchmarkState.Finished): String {
    val sb = StringBuilder()
    sb.append("strategy_id,strategy_name,domain,success,http_status,http_ms,tls_ms,error\n")
    state.results.forEach { r ->
        r.domains.forEach { d ->
            sb.append("${r.strategyId},\"${r.strategyName}\",${d.domain.domain},${d.success},")
            sb.append("${d.attempts.firstNotNullOfOrNull { it.httpStatus } ?: ""},")
            sb.append("${d.medianHttpMs ?: ""},${d.medianTlsMs ?: ""},${d.errorType ?: ""}\n")
        }
    }
    return sb.toString()
}
