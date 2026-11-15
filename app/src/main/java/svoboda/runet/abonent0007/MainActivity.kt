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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.net.Updater
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.ui.home.HomeScreen
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

            fun doConnect(scope: CoroutineScope) {
                scope.launch {
                    manager.connectManual()
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
                    manager.autoConnectOnce()
                }
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
                    onConnect = { requestConnect() },
                    onDisconnect = {
                        controller.disconnect()
                        manager.resetLabel()
                    },
                    onTest = { scope.launch { controller.runTest() } },
                    onSplitToggle = { enabled ->
                        splitEnabled = enabled
                        AppGraph.prefs.splitTunnelEnabled = enabled
                        if (controller.isConnected) {
                            scope.launch { manager.reconnectLastKnown() }
                        }
                    },
                    onPickApps = { showPicker = true },
                    onOpenServers = { showServers = true },
                    onCheckUpdates = {
                        scope.launch {
                            controller.setStatus("Проверка обновлений…")
                            val rel = Updater.checkLatest()
                            if (rel == null) {
                                controller.setStatus("Обновлений нет")
                                return@launch
                            }
                            controller.setStatus("Загрузка ${rel.versionName}…")
                            val file = Updater.download(this@MainActivity, rel.apkUrl) { p ->
                                controller.setStatus("Загрузка ${rel.versionName}: $p%")
                            }
                            if (file == null) {
                                controller.setStatus("Ошибка загрузки")
                                return@launch
                            }
                            controller.setStatus("Установка ${rel.versionName}…")
                            startActivity(Updater.installIntent(this@MainActivity, file))
                        }
                    },
                )

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
                                scope.launch { manager.useWarp() }
                            }
                        },
                        onSelect = { server ->
                            AppGraph.prefs.activeServerId = server.id
                            activeServer = server
                            servers = AppGraph.servers.list()
                            showServers = false
                            val intent = controller.prepareVpn(this@MainActivity)
                            if (intent != null) {
                                consentLauncher.launch(intent)
                            } else {
                                scope.launch { manager.selectServer(server) }
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
