package svoboda3.runet.abonent0007.ui.method

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import svoboda3.runet.abonent0007.AppGraph
import svoboda3.runet.abonent0007.dpi.DpiStrategyAutoSelect
import svoboda3.runet.abonent0007.ui.theme.TextSecondary

/**
 * Подбор DPI-стратегии под YouTube / Google-видео.
 * Перебор идёт без туннеля (отдельный процесс ciadpi), поэтому доступен до подключения
 * и не рвёт текущую сессию. При активной DPI-сессии подбор блокируется (конфликт порта).
 */
@Composable
fun YouTubeStrategyDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val connected = AppGraph.vpn.isConnected

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Стратегия для YouTube / Google-видео") },
        text = {
            Column {
                Text(
                    when {
                        connected && progress == null ->
                            "Сейчас DPI подключён: перебор требует отдельного процесса ciadpi и конфликтует с активной сессией. Отключитесь (ОТКЛЮЧИТЬ) или подберите позже."
                        progress != null -> "Перебор стратегий… $progress"
                        result != null -> result ?: ""
                        else ->
                            "Проверяем стратегии каталога на цели YouTube, без подключения туннеля " +
                                "(отдельный процесс ciadpi). Обычно занимает до нескольких минут."
                    },
                    color = TextSecondary, fontSize = 12.sp,
                )
                if (result != null) Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !connected,
                onClick = {
                    result = null
                    scope.launch {
                        val id = DpiStrategyAutoSelect.pick(AppGraph.dpi) { p ->
                            progress = "${p.index}/${p.total} ${p.id}"
                        }
                        progress = null
                        if (id != null) {
                            AppGraph.prefs.dpiStrategyId = id
                            result = "Подобрано: $id. Применится при следующем подключении."
                        } else {
                            result = "Рабочая стратегия не найдена — текущая остаётся."
                        }
                    }
                },
            ) { Text(if (result == null) "ПОДОБРАТЬ" else "ЗАКРЫТЬ") }
        },
        dismissButton = if (progress == null && result != null) {
            { TextButton(onClick = onDismiss) { Text("ЗАКРЫТЬ") } }
        } else {
            null
        },
    )
}
