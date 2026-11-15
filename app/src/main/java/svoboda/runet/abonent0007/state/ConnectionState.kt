package svoboda.runet.abonent0007.state

enum class VpnSessionPhase { STOPPED, PREPARING, CONNECTING, ESTABLISHING, CONNECTED, DEGRADED, STOPPING, REVOKED, ERROR }
enum class WarpRegistrationPhase { NOT_APPLICABLE, UNREGISTERED, REGISTERING, REGISTERED, INVALID, REGISTRATION_FAILED }
enum class UnderlyingNetworkPhase { UNAVAILABLE, AVAILABLE, VALIDATED, CAPTIVE_PORTAL, LOST }
enum class TunnelTransportPhase { IDLE, RESOLVING, CONNECTING, HANDSHAKING, FLOWING, DEGRADED, LOST, CLOSED, FAILED }

data class ConnectionState(
    val vpn: VpnSessionPhase = VpnSessionPhase.STOPPED,
    val warp: WarpRegistrationPhase = WarpRegistrationPhase.NOT_APPLICABLE,
    val network: UnderlyingNetworkPhase = UnderlyingNetworkPhase.UNAVAILABLE,
    val transport: TunnelTransportPhase = TunnelTransportPhase.IDLE,
)

fun ConnectionState.isFullyConnected(): Boolean =
    vpn == VpnSessionPhase.CONNECTED && transport == TunnelTransportPhase.FLOWING
