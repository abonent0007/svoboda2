package svoboda.runet.abonent0007.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class ConnectionStateRepository {
    private val _vpn = MutableStateFlow(VpnSessionPhase.STOPPED)
    private val _warp = MutableStateFlow(WarpRegistrationPhase.NOT_APPLICABLE)
    private val _network = MutableStateFlow(UnderlyingNetworkPhase.UNAVAILABLE)
    private val _transport = MutableStateFlow(TunnelTransportPhase.IDLE)

    val vpn: StateFlow<VpnSessionPhase> = _vpn.asStateFlow()
    val warp: StateFlow<WarpRegistrationPhase> = _warp.asStateFlow()
    val network: StateFlow<UnderlyingNetworkPhase> = _network.asStateFlow()
    val transport: StateFlow<TunnelTransportPhase> = _transport.asStateFlow()

    fun setVpn(phase: VpnSessionPhase) { _vpn.value = phase }
    fun setWarp(phase: WarpRegistrationPhase) { _warp.value = phase }
    fun setNetwork(phase: UnderlyingNetworkPhase) { _network.value = phase }
    fun setTransport(phase: TunnelTransportPhase) { _transport.value = phase }

    fun snapshot(): ConnectionState = ConnectionState(_vpn.value, _warp.value, _network.value, _transport.value)
}
