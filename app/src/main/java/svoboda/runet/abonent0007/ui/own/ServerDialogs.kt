package svoboda.runet.abonent0007.ui.own

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.own.OwnServerParser
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary

/** Список серверов: WARP по умолчанию + собственные AmneziaWG-локации. */
@Composable
fun ServerListDialog(
    servers: List<OwnServer>,
    activeId: String,
    wrapEnabled: Boolean,
    onSelectWarp: () -> Unit,
    onSelect: (OwnServer) -> Unit,
    onDelete: (OwnServer) -> Unit,
    onAdd: () -> Unit,
    onImport: (List<OwnServer>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val parsed = uris.mapNotNull { importConf(context, it) }
        if (parsed.isNotEmpty()) onImport(parsed)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C121A)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "СЕРВЕРЫ AMNEZIAWG",
                    color = AccentGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Локация сервера определяет страну подключения. " +
                        "Страна внешнего IP показывается отдельно на главном экране.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        ServerRow(
                            title = "🌐 WARP · Cloudflare",
                            subtitle = "Профиль №11 по умолчанию",
                            active = wrapEnabled && activeId.isBlank(),
                            status = "",
                            onClick = onSelectWarp,
                        )
                    }
                    items(servers, key = { it.id }) { server ->
                        ServerRow(
                            title = server.locationTitle,
                            subtitle = listOfNotNull(
                                server.endpointHost.ifBlank { null },
                                server.endpointPort.takeIf { it > 0 }?.toString(),
                            ).joinToString(":"),
                            active = server.id == activeId,
                            status = when (server.lastStatus) {
                                "ok" -> "доступен"
                                "fail" -> "не отвечает"
                                else -> "не проверен"
                            },
                            onClick = { onSelect(server) },
                            onDelete = { onDelete(server) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text("ЗАКРЫТЬ", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/*", "*/*")) },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text("ИМПОРТ .CONF", color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(1.6f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color(0xFF062018),
                        ),
                    ) {
                        Text("ДОБАВИТЬ", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    active: Boolean,
    status: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFF143326) else Elevated,
        border = BorderStroke(1.dp, if (active) AccentGreen else Border),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
                }
                if (active || status.isNotBlank()) {
                    Text(
                        text = if (active) "активен" else status,
                        color = if (active) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить сервер",
                        tint = TextSecondary,
                    )
                }
            }
        }
    }
}

/** Конфигуратор нового собственного сервера. */
@Composable
fun AddServerDialog(
    onSave: (OwnServer) -> Unit,
    onDismiss: () -> Unit,
) {
    var countryName by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var config by remember { mutableStateOf("") }

    val parsed = OwnServerParser.endpoint(config)
    val valid = countryName.isNotBlank() && config.contains("[Peer]", ignoreCase = true) && parsed != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C121A)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "НОВЫЙ СЕРВЕР",
                    color = AccentGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Страна и город — это физическое расположение вашего VPS. " +
                        "Выбор страны здесь не создаёт сервер: вставьте конфиг уже развёрнутого AmneziaWG.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryName,
                        onValueChange = { countryName = it },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        label = { Text("Страна") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { v ->
                            countryCode = v.filter { it.isLetter() }.uppercase().take(2)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Код") },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Город / регион") },
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = config,
                    onValueChange = { config = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("AmneziaWG-конфиг") },
                    placeholder = { Text("[Interface] … [Peer] …", color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                )
                Text(
                    text = parsed?.let { "Endpoint: ${it.first}:${it.second}" }
                        ?: "Endpoint не найден в конфиге",
                    color = if (parsed != null) AccentGreen else TextSecondary,
                    fontSize = 11.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Text("ОТМЕНА", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (valid && parsed != null) {
                                onSave(
                                    OwnServer(
                                        countryName = countryName.trim(),
                                        countryCode = countryCode.trim().uppercase(),
                                        city = city.trim(),
                                        endpointHost = parsed.first,
                                        endpointPort = parsed.second,
                                        config = config.trim(),
                                    ),
                                )
                            }
                        },
                        enabled = valid,
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color(0xFF062018),
                        ),
                    ) {
                        Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val COUNTRY_NAMES = mapOf(
    "CA" to "Kanada", "CH" to "Shvejcariya", "JP" to "Yaponiya",
    "MX" to "Meksika", "NL" to "Niderlandy", "NO" to "Norvegiya",
    "US" to "SShA", "DE" to "Germaniya", "FR" to "Franciya",
    "GB" to "Velikobritaniya", "FI" to "Finlyandiya", "SE" to "Shveciya",
    "IT" to "Italiya", "ES" to "Ispaniya", "AU" to "Avstraliya",
    "SG" to "Singapur", "HK" to "Gonkong", "PL" to "Polsha",
    "RO" to "Rumyniya", "RU" to "Rossiya", "UA" to "Ukraina",
    "BR" to "Brazil", "IN" to "Indiya", "TR" to "Turchiya",
)

private fun importConf(context: android.content.Context, uri: android.net.Uri): OwnServer? {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }.getOrNull() ?: return null
    if (!text.contains("[Peer]", ignoreCase = true)) return null
    val rawName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
    val decoded = java.net.URLDecoder.decode(rawName, "UTF-8")
    val code = decoded.substringBefore('-').trim().uppercase().take(2)
        .ifBlank { decoded.take(2).uppercase() }
    val country = COUNTRY_NAMES[code] ?: code
    val ep = OwnServerParser.endpoint(text)
    return OwnServer(
        countryName = country,
        countryCode = code,
        city = "imported",
        endpointHost = ep?.first.orEmpty(),
        endpointPort = ep?.second ?: 0,
        config = text.trim(),
    )
}
