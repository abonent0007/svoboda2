package svoboda.runet.abonent0007.split

import android.content.Context
import svoboda.runet.abonent0007.data.Prefs

class SplitTunnelManager(
    private val context: Context,
    private val store: SplitTunnelStore,
    private val prefs: Prefs,
) {
    fun resolve(profileId: String): SplitTunnelConfig {
        if (profileId.isBlank()) return SplitTunnelConfig(mode = SplitTunnelMode.ALL)
        store.get(profileId)?.let { return it }
        // Migration from legacy global prefs.
        val legacy = if (prefs.splitTunnelEnabled && prefs.excludedApps.isNotEmpty()) {
            SplitTunnelConfig(
                profileId = profileId,
                mode = SplitTunnelMode.ALL_EXCEPT_SELECTED,
                packageNames = prefs.excludedApps,
                presetId = null,
            )
        } else {
            SplitTunnelConfig(profileId = profileId, mode = SplitTunnelMode.ALL)
        }
        store.set(legacy)
        return legacy
    }

    fun buildInjection(profileId: String): String? {
        val cfg = resolve(profileId)
        if (cfg.mode == SplitTunnelMode.ALL) return null
        val pm = context.packageManager
        val installed = cfg.packageNames.filter { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
        if (installed.isEmpty()) return null
        return when (cfg.mode) {
            SplitTunnelMode.ONLY_SELECTED -> "IncludedApplications = ${installed.joinToString(", ")}"
            SplitTunnelMode.ALL_EXCEPT_SELECTED -> "ExcludedApplications = ${installed.joinToString(", ")}"
            SplitTunnelMode.ALL -> null
        }
    }

    fun update(profileId: String, config: SplitTunnelConfig) {
        if (profileId.isBlank()) return
        store.set(config.copy(profileId = profileId))
    }
}
