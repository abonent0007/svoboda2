package svoboda.runet.abonent0007.ui.method

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import svoboda.runet.abonent0007.ui.home.NeonSwitch
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary

/**
 * Плитка выбора приложений: поиск + список всех установленных приложений с галкой.
 * [selected] — пакеты; [onApply] вызывается при каждом переключении (мгновенное сохранение).
 * [disabled] — пакеты, уже выбранные в другом (парном) списке: строка неактивна,
 * чтобы одно приложение не могло быть одновременно «в туннель» и «напрямую».
 */
@Composable
fun AppScopeTile(
    title: String,
    subtitle: String,
    selected: Set<String>,
    onApply: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    disabled: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    ExpandableTile(
        title = title,
        subtitle = "$subtitle · выбрано: ${selected.size}",
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Поиск приложения или пакета", color = TextSecondary) },
        )
        Text(
            text = "Приложений: ${filtered.size}" + if (selected.isEmpty()) "" else " · выбрано ${selected.size}",
            color = TextSecondary,
            fontSize = 11.sp,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                val locked = app.packageName in disabled && !checked
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (locked) Modifier.alpha(0.45f) else Modifier),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = when {
                        checked -> AccentGreen.copy(alpha = 0.10f)
                        locked -> Elevated.copy(alpha = 0.25f)
                        else -> Elevated.copy(alpha = 0.45f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            checked -> AccentGreen
                            locked -> Border
                            else -> Border
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = app.packageName +
                                    (if (app.system) " · системное" else "") +
                                    (if (locked) " · уже в парном списке" else ""),
                                color = TextSecondary,
                                fontSize = 10.sp,
                            )
                        }
                        NeonSwitch(
                            checked = checked,
                            onCheckedChange = if (locked) null else { on ->
                                onApply(if (on) selected + app.packageName else selected - app.packageName)
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip(
                text = "СБРОСИТЬ",
                onClick = { onApply(emptySet()) },
            )
            ActionChip(
                text = "ВЫБРАТЬ ВСЕ",
                onClick = { onApply(apps.map { it.packageName }.toSet()) },
            )
        }
    }
}
