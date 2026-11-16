package svoboda.runet.abonent0007.ui.split

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.split.RussianDirectCatalog
import svoboda.runet.abonent0007.split.SplitTunnelMode
import svoboda.runet.abonent0007.ui.home.NeonSwitch
import svoboda.runet.abonent0007.ui.home.TileRow
import svoboda.runet.abonent0007.ui.theme.AccentGreen
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.GoodGreen
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary

private data class AppEntry(val pkg: String, val label: String)

/**
 * Подменю «Российские приложения — напрямую».
 *
 * Выбранные приложения исключаются из VPN (`ExcludedApplications`) и ходят
 * в сеть напрямую: банки и госуслуги часто блокируют зарубежные/VPN-адреса,
 * а локальные сервисы работают быстрее без туннеля.
 */
@Composable
fun RussianDirectDialog(
    profileId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val installed = remember {
        runCatching {
            pm.getInstalledApplications(0)
                .associate { it.packageName to pm.getApplicationLabel(it).toString() }
        }.getOrDefault(emptyMap())
    }

    val entriesByCategory = remember {
        RussianDirectCatalog.all.associate { cat ->
            cat.id to cat.packages
                .filter { installed.containsKey(it) }
                .map { AppEntry(it, installed[it].orEmpty()) }
                .sortedBy { it.label.lowercase() }
        }
    }

    val initial = remember(profileId) {
        AppGraph.splitManager.resolve(profileId).packageNames
    }

    // Нормализуем по установленным пакетам и привязываем состояние к профилю.
    var enabled by remember(profileId) {
        mutableStateOf(
            initial.intersect(RussianDirectCatalog.allPackages)
                .intersect(installed.keys)
        )
    }

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
                        text = "Российские приложения — напрямую",
                        color = TextPrimary,
                        fontSize = 19.sp,
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
                    text = "Выбранные приложения исключаются из VPN (direct). Банки, госуслуги и локальные сервисы работают без туннеля.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                RussianDirectCatalog.all.forEach { category ->
                    val apps = entriesByCategory[category.id].orEmpty()
                    val allOn = apps.isNotEmpty() && apps.all { enabled.contains(it.pkg) }
                    val selectedCount = apps.count { enabled.contains(it.pkg) }

                    SectionTitle("${category.title.uppercase()} · $selectedCount/${apps.size}")

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Elevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Все приложения категории", color = TextPrimary, fontSize = 14.sp)
                                Text(
                                    text = if (apps.isEmpty()) "не установлено" else "напрямую: $selectedCount из ${apps.size}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            if (apps.isNotEmpty()) {
                                NeonSwitch(allOn) { value ->
                                    enabled = if (value) enabled + apps.map { it.pkg } else enabled - apps.map { it.pkg }.toSet()
                                }
                            }
                        }
                    }

                    if (apps.isEmpty()) {
                        Text(
                            text = "Ни одно приложение этой категории не найдено на устройстве.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    } else {
                        apps.forEach { app ->
                            val isOn = enabled.contains(app.pkg)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOn) AccentGreen.copy(alpha = 0.12f) else Elevated.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isOn) AccentGreen else Border,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = TextPrimary, fontSize = 13.sp)
                                        Text(app.pkg, color = TextSecondary, fontSize = 10.sp)
                                    }
                                    NeonSwitch(isOn) { value ->
                                        enabled = if (value) enabled + app.pkg else enabled - app.pkg
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoodGreen),
                    onClick = {
                        val current = AppGraph.splitManager.resolve(profileId)
                        // Сохраняем пакеты, которые пользователь исключил ранее,
                        // но которых нет в каталоге.
                        val preserved = current.packageNames - RussianDirectCatalog.allPackages
                        val packageNames = preserved + enabled
                        val cfg = current.copy(
                            mode = if (packageNames.isEmpty()) SplitTunnelMode.ALL else SplitTunnelMode.ALL_EXCEPT_SELECTED,
                            packageNames = packageNames,
                            presetId = "russian_direct",
                        )
                        AppGraph.splitManager.update(profileId, cfg)
                        if (AppGraph.vpn.isConnected) {
                            // Не используем scope диалога (он умирает при закрытии).
                            AppGraph.appScope.launch { AppGraph.reconnect.userConnect(false) }
                        }
                        onDismiss()
                    },
                ) {
                    Text(
                        text = if (enabled.isEmpty()) "ПРИМЕНИТЬ · весь трафик через VPN" else "ПРИМЕНИТЬ · ${enabled.size} напрямую",
                        color = TextPrimary,
                        fontSize = 13.sp,
                    )
                }

                TileRow(
                    title = "Справка",
                    value = "Direct-режим использует ExcludedApplications; изменения применяются после переподключения.",
                    opaque = true,
                )
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
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}
