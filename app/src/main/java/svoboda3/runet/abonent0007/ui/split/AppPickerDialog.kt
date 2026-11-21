package svoboda3.runet.abonent0007.ui.split

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import svoboda3.runet.abonent0007.ui.home.NeonSwitch
import svoboda3.runet.abonent0007.ui.theme.AccentGreen
import svoboda3.runet.abonent0007.ui.theme.Border
import svoboda3.runet.abonent0007.ui.theme.Elevated
import svoboda3.runet.abonent0007.ui.theme.TextPrimary
import svoboda3.runet.abonent0007.ui.theme.TextSecondary

private data class LaunchableApp(val packageName: String, val label: String)

/** Список приложений, которые должны идти мимо VPN. */
@Composable
fun AppPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var picked by remember { mutableStateOf(selected) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

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
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "МИМО VPN",
                    color = AccentGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "Выбранные приложения пойдут напрямую, не через туннель",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск приложения", color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Elevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                                NeonSwitch(
                                    checked = app.packageName in picked,
                                    onCheckedChange = { on ->
                                        picked = if (on) picked + app.packageName
                                        else picked - app.packageName
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { picked = emptySet() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    ) {
                        Text("СБРОС", color = TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { onApply(picked) },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color(0xFF062018),
                        ),
                    ) {
                        Text("ГОТОВО · ${picked.size}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.applicationInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.applicationInfo.packageName,
                    label = it.loadLabel(pm).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())
}
