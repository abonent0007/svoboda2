package svoboda.runet.abonent0007.ui.method

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary
import svoboda.runet.abonent0007.warp.ServiceIpRanges

/**
 * Выбор сервисов, чей трафик заворачивается в туннель (раздельное туннелирование по маршрутам).
 * Остальные приложения идут напрямую — маршруты строятся по AllowedIPs этих сервисов.
 */
@Composable
fun ServiceScopePicker(title: String = "СЕРВИСЫ В ТУННЕЛЬ") {
    val prefs = AppGraph.prefs
    var scope by remember { mutableStateOf(prefs.tunnelServiceScope) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Только трафик выбранных сервисов пойдёт через туннель, остальное — напрямую " +
                "(маршруты строятся по AllowedIPs). Диапазоны IP взяты из публичного проекта " +
                "nellimonix/warp-config-generator-vercel (MIT).",
            color = TextSecondary,
            fontSize = 11.sp,
        )
        ServiceIpRanges.catalog.chunked(2).forEach { rowServices ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowServices.forEach { service ->
                    val selected = scope.contains(service.id)
                    ActionChip(
                        text = service.title,
                        selected = selected,
                        onClick = {
                            scope = if (selected) scope - service.id else scope + service.id
                            prefs.tunnelServiceScope = scope
                        },
                    )
                }
            }
        }
        if (scope.isNotEmpty()) {
            ActionChip(
                text = "СБРОСИТЬ ВЫБОР",
                onClick = {
                    scope = emptySet()
                    prefs.tunnelServiceScope = scope
                },
            )
        }
    }
}

/** Фильтр профилей по измеренной стране выхода (NL / FI / US и прочие). */
@Composable
fun CountryFilterRow(
    countries: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    if (countries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "СТРАНА ВЫХОДА (измерено)",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            countries.forEach { code ->
                val active = selected.contains(code)
                TextButton(onClick = {
                    onChange(if (active) selected - code else selected + code)
                }) {
                    Text(
                        text = code,
                        fontSize = 12.sp,
                        color = if (active) AccentGreen else TextSecondary,
                    )
                }
            }
        }
    }
}
