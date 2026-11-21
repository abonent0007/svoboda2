package svoboda.runet.abonent0007.dpi

import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.split.SplitTunnelConfig
import svoboda.runet.abonent0007.split.SplitTunnelMode

/**
 * Генерирует синтетический AWG-конфиг для DPI-режима.
 *
 * Особенности:
 *  - Dummy [Peer] с `AllowedIPs = 0.0.0.0/0` — иначе GoBackend не добавит маршруты в TUN.
 *  - AWG-процесс не запускается (`wgTurnOn` не вызывается в DPI-режиме) — ключи фиктивные.
 *  - Собственный пакет приложения исключается в режимах ALL и ALL_EXCEPT_SELECTED.
 *    В режиме ONLY_SELECTED (allowlist) собственный пакет и так не попадает в туннель —
 *    одновременно addAllowedApplication и addDisallowedApplication вызвать нельзя.
 */
object DpiConfig {

    /** 32 байта нулей в base64 — валидный ключ, никогда не используется. */
    private const val DUMMY_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private const val OWN_PACKAGE = "svoboda.runet.abonent0007"

    fun build(
        prefs: Prefs,
        split: SplitTunnelConfig,
        includedApps: Set<String> = emptySet(),
    ): String {
        val mtu = prefs.tunnelMtu.coerceIn(1200, 1480)
        val dns = if (prefs.dpiMapDns) "198.18.0.2" else prefs.dnsServers.ifBlank { "1.1.1.1, 1.0.0.1" }

        val excluded = LinkedHashSet<String>()
        if (split.mode == SplitTunnelMode.ALL_EXCEPT_SELECTED) {
            excluded += split.packageNames.filter { it.isNotBlank() }
        }

        // Единый allowlist: «только выбранные» из split-tunnel + per-app список «В туннель».
        val included = LinkedHashSet<String>()
        if (split.mode == SplitTunnelMode.ONLY_SELECTED) {
            included += split.packageNames.filter { it.isNotBlank() }
        }
        included += includedApps.filter { it.isNotBlank() }
        val allow = included - OWN_PACKAGE

        // Пока allowlist непуст, приложение само не в allowlist → его трафик идёт напрямую,
        // и добавлять себя в ExcludedApplications нельзя (иначе Android получит и allow, и deny).
        if (allow.isEmpty() && split.mode != SplitTunnelMode.ONLY_SELECTED) {
            excluded += OWN_PACKAGE
        }

        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $DUMMY_KEY")
            appendLine("Address = 10.0.0.2/32")
            appendLine("DNS = $dns")
            appendLine("MTU = $mtu")
            if (excluded.isNotEmpty()) {
                appendLine("ExcludedApplications = ${excluded.joinToString(", ")}")
            }
            if (allow.isNotEmpty()) {
                appendLine("IncludedApplications = ${allow.joinToString(", ")}")
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $DUMMY_KEY")
            appendLine("AllowedIPs = 0.0.0.0/0")
        }
    }
}
