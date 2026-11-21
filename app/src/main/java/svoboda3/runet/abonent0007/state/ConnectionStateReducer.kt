package svoboda3.runet.abonent0007.state

object ConnectionStateReducer {
    fun reduce(state: ConnectionState): VpnSessionPhase {
        // CONNECTED only if vpn connected AND transport flowing
        return if (state.vpn == VpnSessionPhase.CONNECTED && state.transport == TunnelTransportPhase.FLOWING) {
            VpnSessionPhase.CONNECTED
        } else if (state.vpn == VpnSessionPhase.CONNECTED && state.transport != TunnelTransportPhase.FLOWING) {
            VpnSessionPhase.DEGRADED
        } else {
            state.vpn
        }
    }
}
