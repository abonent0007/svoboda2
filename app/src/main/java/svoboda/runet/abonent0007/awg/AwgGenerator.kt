package svoboda.runet.abonent0007.awg

import kotlin.random.Random
import svoboda.runet.abonent0007.warp.WarpIdentity

/** Предустановка AWG 2.0 (как в публичном генераторе). */
enum class AwgPreset(val id: String, val label: String, val description: String) {
    V1("awg2_v1", "AWG 2.0 · вариант 1", "Штатная I1-маска"),
    V2("awg2_v2", "AWG 2.0 · вариант 2", "QUIC-маска (SNI)"),
    V3("awg2_v3", "AWG 2.0 · вариант 3", "SIP INVITE I1+I2"),
}

/** Страна/сервер выхода. */
data class AwgCountry(
    val id: String,
    val title: String,
    /** Пустой host — случайный anycast Cloudflare (без регистрации). */
    val host: String,
    val port: Int,
    val needsRegistration: Boolean,
    /** ISO-код для флага (у генератора «FL» вместо «FI»). */
    val iso: String = "",
)

/**
 * Автогенерация AWG-конфига по образцу warp-generation:
 * identity (приватный ключ) → сервер страны → 3 предустановки маскировки.
 */
object AwgGenerator {

    val COUNTRIES: List<AwgCountry> = listOf(
        AwgCountry("def", "Стандартный", "", 0, false),
        AwgCountry("FL", "🇫🇮 Финляндия", "fi.tribukvy.ltd", 500, true, "FI"),
        AwgCountry("NL", "🇳🇱 Нидерланды", "nl.tribukvy.ltd", 500, true, "NL"),
        AwgCountry("PL", "🇵🇱 Польша", "pl.tribukvy.ltd", 500, true, "PL"),
        AwgCountry("LV", "🇱🇻 Латвия", "lv.tribukvy.ltd", 500, true, "LV"),
        AwgCountry("DE", "🇩🇪 Германия", "de.tribukvy.ltd", 500, true, "DE"),
        AwgCountry("EE", "🇪🇪 Эстония", "ee.tribukvy.ltd", 500, true, "EE"),
        AwgCountry("RU", "🇷🇺 Россия", "ru0.tribukvy.ltd", 500, true, "RU"),
        AwgCountry("lteFL", "[LTE] 🇫🇮 Финляндия", "tel.fi.tribukvy.ltd", 500, true, "FI"),
        AwgCountry("ltePL", "[LTE] 🇵🇱 Польша", "tel.pl.tribukvy.ltd", 500, true, "PL"),
        AwgCountry("lteDE", "[LTE] 🇩🇪 Германия", "tel.de.tribukvy.ltd", 500, true, "DE"),
    )

    private val ANYCAST_PREFIXES = listOf(
        "162.159.192.", "162.159.195.", "engage.cloudflareclient.com",
        "8.6.112.", "8.34.70.", "8.34.146.", "8.35.211.", "8.39.125.", "8.39.204.",
        "8.39.214.", "8.47.69.", "188.114.96.", "188.114.97.", "188.114.98.", "188.114.99.",
    )

    private val ANYCAST_PORTS = listOf(
        500, 854, 859, 864, 878, 880, 890, 891, 894, 903, 908, 928, 934, 939, 942, 943,
        945, 946, 955, 968, 987, 988, 1002, 1010, 1014, 1018, 1070, 1074, 1180, 1387,
        1701, 1843, 2371, 2408, 2506, 3138, 3476, 3581, 3854, 4177, 4198, 4233, 4500,
        5279, 5956, 7103, 7152, 7156, 7281, 7559, 8319, 8742, 8854, 8886,
    )

    /**
     * Случайный endpoint для страны. Для «Стандартный» — публичный anycast Cloudflare,
     * для остальных — фиксированный хост с произвольным портом.
     */
    fun randomEndpoint(country: AwgCountry, rnd: Random = Random.Default): Pair<String, Int> {
        if (country.host.isNotBlank()) {
            val port = if (country.port > 0) country.port else ANYCAST_PORTS[rnd.nextInt(ANYCAST_PORTS.size)]
            return country.host to port
        }
        val prefix = ANYCAST_PREFIXES[rnd.nextInt(ANYCAST_PREFIXES.size)]
        val host = if (prefix.contains('.')) "$prefix${rnd.nextInt(10) + 1}" else prefix
        return host to ANYCAST_PORTS[rnd.nextInt(ANYCAST_PORTS.size)]
    }

    /** I1/I2 для предустановки. */
    private fun mask(preset: AwgPreset, rnd: Random): Pair<String, String> = when (preset) {
        AwgPreset.V1 -> AwgPresets.V1_I1 to ""
        AwgPreset.V3 -> AwgPresets.V3_I1 to AwgPresets.V3_I2
        AwgPreset.V2 -> {
            val sni = AwgPresets.WIRESOCK_DOMAINS[rnd.nextInt(AwgPresets.WIRESOCK_DOMAINS.size)]
            AwgQuicMask.generate(sni) to ""
        }
    }

    fun buildConfig(
        identity: WarpIdentity,
        country: AwgCountry,
        preset: AwgPreset,
        dns: String,
        mtu: Int,
        ipv6: Boolean,
        keepalive: Int?,
        rnd: Random = Random.Default,
    ): String {
        val (host, port) = randomEndpoint(country, rnd)
        val (i1, i2) = mask(preset, rnd)
        val addresses = buildList {
            identity.ipv4.takeIf { it.isNotBlank() }?.let { add("$it/32") }
            if (ipv6) identity.ipv6.takeIf { it.isNotBlank() }?.let { add("$it/128") }
        }
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${identity.privateKey}")
            if (addresses.isNotEmpty()) appendLine("Address = ${addresses.joinToString(", ")}")
            if (dns.isNotBlank()) appendLine("DNS = $dns")
            appendLine("MTU = $mtu")
            appendLine("S1 = 0")
            appendLine("S2 = 0")
            appendLine("S3 = 0")
            appendLine("S4 = 0")
            appendLine("Jc = 4")
            appendLine("Jmin = 40")
            appendLine("Jmax = 70")
            appendLine("H1 = 1")
            appendLine("H2 = 2")
            appendLine("H3 = 3")
            appendLine("H4 = 4")
            if (i1.isNotBlank()) appendLine("I1 = $i1")
            if (i2.isNotBlank()) appendLine("I2 = $i2")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${identity.peerPublicKey}")
            appendLine("AllowedIPs = ${if (ipv6) "0.0.0.0/0, ::/0" else "0.0.0.0/0"}")
            appendLine("Endpoint = $host:$port")
            if (keepalive != null && keepalive > 0) appendLine("PersistentKeepalive = $keepalive")
        }
    }
}
