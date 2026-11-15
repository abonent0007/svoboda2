package svoboda.runet.abonent0007

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.own.OwnServerStore
import svoboda.runet.abonent0007.vpn.ConnectionManager
import svoboda.runet.abonent0007.vpn.ReconnectCoordinator
import svoboda.runet.abonent0007.vpn.VpnController
import svoboda.runet.abonent0007.warp.BundledSeed
import svoboda.runet.abonent0007.warp.GeneratedProfileStore
import svoboda.runet.abonent0007.warp.SeedCatalog

/** Простой граф зависимостей (без DI-фреймворка). */
object AppGraph {
    lateinit var vpn: VpnController
    lateinit var manager: ConnectionManager
    lateinit var reconnect: ReconnectCoordinator
    lateinit var prefs: Prefs
    lateinit var seeds: List<BundledSeed>
    lateinit var servers: OwnServerStore
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob())
}

class SvobodaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.prefs = Prefs(this)
        AppGraph.vpn = VpnController(this, AppGraph.prefs)
        AppGraph.seeds = SeedCatalog.load(this)
        AppGraph.servers = OwnServerStore(this)
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
        AppGraph.reconnect.startNetworkMonitoring()
        AppGraph.vpn.startMonitor(AppGraph.appScope)
    }
}
