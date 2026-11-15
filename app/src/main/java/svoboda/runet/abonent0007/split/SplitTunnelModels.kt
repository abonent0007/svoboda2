package svoboda.runet.abonent0007.split

import kotlinx.serialization.Serializable

enum class SplitTunnelMode { ALL, ONLY_SELECTED, ALL_EXCEPT_SELECTED }

@Serializable
data class SplitTunnelConfig(
    val profileId: String = "",
    val mode: SplitTunnelMode = SplitTunnelMode.ALL,
    val packageNames: Set<String> = emptySet(),
    val presetId: String? = null,
    val updatedAt: Long = 0L,
)
