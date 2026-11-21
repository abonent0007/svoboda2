package svoboda.runet.abonent0007.dpi

import svoboda.runet.abonent0007.data.Prefs

/**
 * Базовый синтетический AWG-конфиг для DPI-режима.
 *
 * Особенности:
 *  - Dummy [Peer] с `AllowedIPs = 0.0.0.0/0` — иначе GoBackend не добавит маршруты в TUN.
 *  - AWG-процесс не запускается (`wgTurnOn` не вызывается в DPI-режиме) — ключи фиктивные.
 *
 * Списки приложений (IncludedApplications/ExcludedApplications) добавляются централизованно
 * в VpnController.applySplitTunnel — единая логика для всех методов.
 */
object DpiConfig {

    /** 32 байта нулей в base64 — валидный ключ, никогда не используется. */
    private const val DUMMY_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    fun build(prefs: Prefs): String {
        val mtu = prefs.tunnelMtu.coerceIn(1200, 1480)
        val dns = if (prefs.dpiMapDns) "198.18.0.2" else prefs.dnsServers.ifBlank { "1.1.1.1, 1.0.0.1" }
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $DUMMY_KEY")
            appendLine("Address = 10.0.0.2/32")
            appendLine("DNS = $dns")
            appendLine("MTU = $mtu")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $DUMMY_KEY")
            appendLine("AllowedIPs = 0.0.0.0/0")
        }
    }
}
