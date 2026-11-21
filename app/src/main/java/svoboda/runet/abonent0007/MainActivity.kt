package svoboda.runet.abonent0007

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.net.Updater
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.ui.home.HomeScreen
import svoboda.runet.abonent0007.ui.method.YouTubeStrategyDialog
import svoboda.runet.abonent0007.ui.own.AddServerDialog
import svoboda.runet.abonent0007.ui.own.ServerListDialog
import svoboda.runet.abonent0007.ui.split.AppPickerDialog
import svoboda.runet.abonent0007.ui.theme.SvobodaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = AppGraph.vpn
        val manager = AppGraph.manager
        val reconnect = AppGraph.reconnect

        setContent {
            val phase by controller.phase.collectAsState()
            val error by controller.error.collectAsState()
            val exit by controller.exit.collectAsState()
            val lastPing by controller.lastPing.collectAsState()
            val pingHistory by controller.pingHistory.collectAsState()
            val testResult by controller.testResult.collectAsState()
            val profileLabel by manager.profileLabel.collectAsState()
            val scope = rememberCoroutineScope()

            var splitEnabled by remember { mutableStateOf(AppGraph.prefs.splitTunnelEnabled) }
            var excluded by remember { mutableStateOf(AppGraph.prefs.excludedApps) }
            var showPicker by remember { mutableStateOf(false) }
            var servers by remember { mutableStateOf(AppGraph.servers.list()) }
            var activeServer by remember { mutableStateOf(AppGraph.manager.activeServer()) }
            var showServers by remember { mutableStateOf(false) }
            var showAddServer by remember { mutableStateOf(false) }
            var updateMessage by remember { mutableStateOf<String?>(null) }
            var showYtPrompt by remember { mutableStateOf(true) }
            var showYtPicker by remember { mutableStateOf(false) }

            fun doConnect(scope: CoroutineScope) {
                // Выбранный на главном экране способ подключения:
                // WARP — принудительно идём через WARP (снимаем выбранный AWG-сервер),
                // AWG — используем активный сервер, если он есть (иначе WARP как fallback).
                when (AppGraph.prefs.connectionMethod) {
                    "WARP" -> AppGraph.prefs.activeServerId = ""
                    "AWG" -> Unit
                }
                scope.launch {
                    reconnect.userConnect(true)
                }
            }

            val consentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                doConnect(scope)
            }

            fun requestConnect() {
                val intent = controller.prepareVpn(this)
                if (intent != null) {
                    consentLauncher.launch(intent)
                } else {
                    doConnect(scope)
                }
            }

            LaunchedEffect(Unit) {
                if (controller.prepareVpn(this@MainActivity) == null) {
                    reconnect.userConnect(false)
                }
            }

            // Автообновление: тихая проверка GitHub Releases при запуске.
            LaunchedEffect(Unit) {
                if (!AppGraph.prefs.autoUpdateEnabled) return@LaunchedEffect
                kotlinx.coroutines.delay(5_000) // не мешаем первому кадру и автоподключению
                val rel = Updater.checkLatest() ?: return@LaunchedEffect
                controller.setStatus("Обновление ${rel.versionName}: загрузка…")
                val file = Updater.download(this@MainActivity, rel.apkUrl) { p ->
                    controller.setStatus("Обновление ${rel.versionName}: $p%")
                }
                if (file == null) {
                    controller.setStatus("Не удалось скачать обновление")
                    return@LaunchedEffect
                }
                controller.setStatus("Обновление ${rel.versionName}: установка…")
                startActivity(Updater.installIntent(this@MainActivity, file))
            }

            SvobodaTheme {
                HomeScreen(
                    phase = phase,
                    error = error,
                    profileLabel = profileLabel,
                    exitIp = exit?.ip,
                    country = exit?.country?.let { NetProbe.countryLabel(it) },
                    lastPing = lastPing,
                    pingSamples = pingHistory,
                    testResult = testResult,
                    prefs = AppGraph.prefs,
                    splitEnabled = splitEnabled,
                    excludedCount = excluded.size,
                    serverTitle = activeServer?.locationTitle,
                    splitProfileId = activeServer?.id ?: "warp",
                    onConnect = { requestConnect() },
                    onDisconnect = {
                        scope.launch { reconnect.userDisconnect() }
                    },
                    onTest = { scope.launch { controller.runTest() } },
                    onSplitToggle = { enabled ->
                        splitEnabled = enabled
                        AppGraph.prefs.splitTunnelEnabled = enabled
                        if (controller.isConnected) {
                            scope.launch { reconnect.userConnect(false) }
                        }
                    },
                    onPickApps = { showPicker = true },
                    onOpenServers = { showServers = true },
                    updateMessage = updateMessage,
                    onDismissUpdate = { updateMessage = null },
                    onCheckUpdates = {
                        scope.launch {
                            updateMessage = "Обновляю…"
                            val rel = Updater.checkLatest()
                            if (rel == null) {
                                updateMessage = "Уже установлена последняя версия"
                                return@launch
                            }
                            val file = Updater.download(this@MainActivity, rel.apkUrl) { p ->
                                updateMessage = "Обновляю… $p%"
                            }
                            if (file == null) {
                                updateMessage = "Не удалось скачать обновление"
                                return@launch
                            }
                            updateMessage = "Скачано. Открываю установщик…"
                            startActivity(Updater.installIntent(this@MainActivity, file))
                        }
                    },
                )

                if (showYtPrompt) {
                    AlertDialog(
                        onDismissRequest = { showYtPrompt = false },
                        title = { Text("Улучшить работу YouTube?") },
                        text = {
                            Text(
                                "Подбор стратегии DPI под YouTube / Google-видео: перебор ~43 стратегий, " +
                                    "обычно до нескольких минут. Если DPI сейчас подключён, подбор лучше запустить " +
                                    "после отключения. Это можно сделать и позже: Настройки → DPI → " +
                                    "«Подобрать стратегию для YouTube».",
                                color = androidx.compose.ui.graphics.Color(0xFF9FB0C2),
                                fontSize = 12.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showYtPrompt = false
                                showYtPicker = true
                            }) { Text("ПОДОБРАТЬ") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showYtPrompt = false }) { Text("ПОЗЖЕ") }
                        },
                    )
                }
                if (showYtPicker) {
                    YouTubeStrategyDialog(onDismiss = { showYtPicker = false })
                }

                if (showServers) {
                    ServerListDialog(
                        servers = servers,
                        activeId = activeServer?.id.orEmpty(),
                        wrapEnabled = activeServer == null,
                        onSelectWarp = {
                            AppGraph.prefs.activeServerId = ""
                            activeServer = null
                            servers = AppGraph.servers.list()
                            showServers = false
                            manager.resetLabel()
                            if (controller.isConnected) {
                                scope.launch { reconnect.userConnect(false) }
                            }
                        },
                        onSelect = { server ->
                            AppGraph.prefs.activeServerId = server.id
                            activeServer = server
                            servers = AppGraph.servers.list()
                            showServers = false
                            val intent = controller.prepareVpn(this@MainActivity)
                            if (intent != null) {
                                consentLauncher.launch(intent as android.content.Intent)
                            } else {
                                scope.launch { reconnect.userConnect(true) }
                            }
                        },
                        onDelete = { server ->
                            AppGraph.servers.remove(server.id)
                            servers = AppGraph.servers.list()
                            if (activeServer?.id == server.id) {
                                AppGraph.prefs.activeServerId = ""
                                activeServer = null
                                manager.resetLabel()
                            }
                        },
                        onAdd = { showAddServer = true },
                        onImport = { imported ->
                            imported.forEach { AppGraph.servers.add(it) }
                            servers = AppGraph.servers.list()
                        },
                        onDismiss = { showServers = false },
                    )
                }

                if (showAddServer) {
                    AddServerDialog(
                        onSave = { server ->
                            val added = AppGraph.servers.add(server)
                            servers = AppGraph.servers.list()
                            activeServer = added
                            showAddServer = false
                        },
                        onDismiss = { showAddServer = false },
                    )
                }
            }
        }
    }
}
