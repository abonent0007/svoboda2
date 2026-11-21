package svoboda3.runet.abonent0007.vpn

import kotlin.runCatching
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.GoBackend
import svoboda3.runet.abonent0007.AppGraph
import svoboda3.runet.abonent0007.state.ConnectionStateRepository
import svoboda3.runet.abonent0007.state.VpnSessionPhase

class VpnLifecycleCallback(
    private val stateRepo: ConnectionStateRepository,
) : GoBackend.LifecycleCallback {
    override fun onVpnRevoked() {
        stateRepo.setVpn(VpnSessionPhase.REVOKED)
        AppGraph.appScope.launch {
            runCatching { AppGraph.reconnect.userDisconnect() }
        }
    }

    override fun onAlwaysOnTriggered() {
        // Async restore handled by AppGraph; here just mark
        stateRepo.setVpn(VpnSessionPhase.PREPARING)
    }

    /**
     * FGS не поднялся: VPN не должен оставаться в «полуинициализированном» состоянии
     * и UI не должен показывать CONNECTED.
     */
    override fun onForegroundFailed(reason: String) {
        android.util.Log.e("SvobodaVpn", "Foreground service failed: $reason")
        stateRepo.setVpn(VpnSessionPhase.ERROR)
        AppGraph.appScope.launch {
            runCatching { AppGraph.reconnect.userDisconnect() }
        }
    }
}
