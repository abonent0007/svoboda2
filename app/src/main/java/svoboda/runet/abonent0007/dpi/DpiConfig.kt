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
        allowedIpsOverride: String? = null,
    ): String {
        val mtu = prefs.tunnelMtu.coerceIn(1200, 1480)
        val dns = if (prefs.dpiMapDns) "198.18.0.2" else prefs.dnsServers.ifBlank { "1.1.1.1, 1.0.0.1" }

        val excluded = LinkedHashSet<String>()
        if (split.mode == SplitTunnelMode.ALL_EXCEPT_SELECTED) {
            excluded += split.packageNames.filter { it.isNotBlank() }
        }
        if (split.mode != SplitTunnelMode.ONLY_SELECTED) {
            // ALL и ALL_EXCEPT_SELECTED: свой пакет напрямую, иначе ciadpi-сокеты зашли бы в TUN (рекурсия).
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
            if (split.mode == SplitTunnelMode.ONLY_SELECTED) {
                // Пользовательские "только выбранные": own пакет не добавляем в allowlist — интернет остаётся напрямую.
                val included = split.packageNames.filter { it.isNotBlank() }
                if (included.isNotEmpty()) {
                    appendLine("IncludedApplications = ${included.joinToString(", ")}")
                }
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $DUMMY_KEY")
            appendLine("AllowedIPs = ${allowedIpsOverride ?: "0.0.0.0/0"}")
        }
    }
}
