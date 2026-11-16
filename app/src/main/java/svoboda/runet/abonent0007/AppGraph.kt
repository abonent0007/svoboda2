package svoboda.runet.abonent0007

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.state.ConnectionStateRepository
import svoboda.runet.abonent0007.split.SplitTunnelManager
import svoboda.runet.abonent0007.split.SplitTunnelStore
import svoboda.runet.abonent0007.own.OwnServerStore
import svoboda.runet.abonent0007.vpn.ConnectionManager
import svoboda.runet.abonent0007.vpn.ProfileTestCoordinator
import svoboda.runet.abonent0007.vpn.ReconnectCoordinator
import svoboda.runet.abonent0007.vpn.VpnController
import svoboda.runet.abonent0007.vpn.VpnLifecycleCallback
import org.amnezia.awg.backend.GoBackend
import svoboda.runet.abonent0007.warp.BundledSeed
import svoboda.runet.abonent0007.warp.GeneratedProfileStore
import svoboda.runet.abonent0007.warp.SeedCatalog

/** Простой граф зависимостей (без DI-фреймворка). */
object AppGraph {
    lateinit var connectionState: ConnectionStateRepository
    lateinit var vpn: VpnController
    lateinit var manager: ConnectionManager
    lateinit var reconnect: ReconnectCoordinator
    lateinit var profileTester: ProfileTestCoordinator
    lateinit var prefs: Prefs
    lateinit var seeds: List<BundledSeed>
    lateinit var servers: OwnServerStore
    lateinit var splitManager: SplitTunnelManager
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob())
}

class SvobodaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.prefs = Prefs(this)
        AppGraph.connectionState = ConnectionStateRepository()
        AppGraph.vpn = VpnController(this, AppGraph.prefs, AppGraph.connectionState)
        AppGraph.seeds = SeedCatalog.load(this)
        AppGraph.servers = OwnServerStore(this)
        AppGraph.splitManager = SplitTunnelManager(this, SplitTunnelStore(this), AppGraph.prefs)
        AppGraph.manager = ConnectionManager(
            controller = AppGraph.vpn,
            store = GeneratedProfileStore(this),
            seeds = AppGraph.seeds,
            prefs = AppGraph.prefs,
            serverStore = AppGraph.servers,
        )
        AppGraph.reconnect = ReconnectCoordinator(
            context = this,
            controller = AppGraph.vpn,
            prefs = AppGraph.prefs,
            scope = AppGraph.appScope,
            manualConnect = { AppGraph.manager.connectManual() },
            autoConnect = { AppGraph.manager.connectLastKnown() },
            disconnectAction = { AppGraph.vpn.disconnect() },
        )
        GoBackend.setLifecycleCallback(VpnLifecycleCallback(AppGraph.connectionState))
        GoBackend.setAlwaysOnCallback(object : GoBackend.AlwaysOnCallback {
            override fun alwaysOnTriggered() {
                AppGraph.appScope.launch {
                    if (AppGraph.prefs.autoConnect) AppGraph.reconnect.userConnect(false)
                }
            }
        })
        AppGraph.profileTester = ProfileTestCoordinator(
            controller = AppGraph.vpn,
            reconnect = AppGraph.reconnect,
            prefs = AppGraph.prefs,
        )
        AppGraph.reconnect.startNetworkMonitoring()
        AppGraph.vpn.startMonitor(AppGraph.appScope)
    }
}
