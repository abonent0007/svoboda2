package svoboda.runet.abonent0007.warp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Шаблоны генерации WARP-конфигов «Свободы 2.0».
 *
 * Логика собрана по образцам публичных WARP-генераторов (v1/v2/v3 = 1/2/3
 * I-параметра, AWG 2.0-опции, LTE-режим с пониженным MTU и маршрутизацией
 * только диапазонов Cloudflare), но реализована нами.
 */
data class WarpTemplate(
    val id: String,
    val title: String,
    val description: String,
    /** Сколько I-параметров (0 = AWG 1.0 без магических пакетов). */
    val iParamCount: Int,
    /** AWG 2.0: RandomTrailers + DisableCookies. */
    val awg2: Boolean,
    val mtu: Int,
    /** AllowedIPs = только диапазоны Cloudflare (остальное — напрямую). */
    val cloudflareOnly: Boolean,
    /** PersistentKeepalive, секунды; 0 = не задавать. */
    val persistentKeepalive: Int,
)

object WarpTemplates {

    val STANDARD = WarpTemplate(
        id = "standard",
        title = "Стандартный (AWG 1.0)",
        description = "Базовый AWG: Jc/Jmin/Jmax + S/H-параметры, весь трафик в туннель.",
        iParamCount = 0,
        awg2 = false,
        mtu = 1420,
        cloudflareOnly = false,
        persistentKeepalive = 0,
    )

    val AWG2_ONE = WarpTemplate(
        id = "awg2_1",
        title = "AWG 2.0 · 1 параметр",
        description = "Магический I1 в формате QUIC Initial + RandomTrailers/DisableCookies.",
        iParamCount = 1,
        awg2 = true,
        mtu = 1420,
        cloudflareOnly = false,
        persistentKeepalive = 0,
    )

    val AWG2_TWO = WarpTemplate(
        id = "awg2_2",
        title = "AWG 2.0 · 2 параметра",
        description = "I1 (QUIC Initial) + I2 (фейковый SIP 100 Trying).",
        iParamCount = 2,
        awg2 = true,
        mtu = 1420,
        cloudflareOnly = false,
        persistentKeepalive = 0,
    )

    val AWG2_THREE = WarpTemplate(
        id = "awg2_3",
        title = "AWG 2.0 · 3 параметра",
        description = "I1 (QUIC Initial) + I2 (SIP 100 Trying) + I3 (SIP 200 OK).",
        iParamCount = 3,
        awg2 = true,
        mtu = 1420,
        cloudflareOnly = false,
        persistentKeepalive = 0,
    )

    val LTE = WarpTemplate(
        id = "lte",
        title = "LTE (облегчённый)",
        description = "MTU 1280, keepalive 25 c, в туннель уходят только диапазоны Cloudflare — " +
            "скорость LTE не расходуется на весь трафик.",
        iParamCount = 1,
        awg2 = true,
        mtu = 1280,
        cloudflareOnly = true,
        persistentKeepalive = 25,
    )

    val LTE_AWG2 = WarpTemplate(
        id = "lte_awg2",
        title = "LTE + 3 параметра",
        description = "Как LTE, но с I1/I2/I3.",
        iParamCount = 3,
        awg2 = true,
        mtu = 1280,
        cloudflareOnly = true,
        persistentKeepalive = 25,
    )

    val all = listOf(STANDARD, AWG2_ONE, AWG2_TWO, AWG2_THREE, LTE, LTE_AWG2)

    fun byId(id: String): WarpTemplate = all.firstOrNull { it.id == id } ?: STANDARD

    /** Фейковые SIP-сообщения (RFC 3261, §24.1–24.2) для I2/I3. */
    fun sipTrying(): String = buildString {
        append("SIP/2.0 100 Trying\r\n")
        append("Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\r\n")
        append("From: Alice <sip:alice@atlanta.com>;tag=1928301774\r\n")
        append("To: Bob <sip:bob@biloxi.com>\r\n")
        append("Call-ID: a84b4c76f667cs13@pc33.atlanta.com\r\n")
        append("CSeq: 314159 INVITE\r\n")
        append("Content-Length: 0\r\n\r\n")
    }

    fun sipOk(): String = buildString {
        append("SIP/2.0 200 OK\r\n")
        append("Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\r\n")
        append("From: Alice <sip:alice@atlanta.com>;tag=1928301774\r\n")
        append("To: Bob <sip:bob@biloxi.com>;tag=a6c85cf\r\n")
        append("Call-ID: a84b4c76f667cs13@pc33.atlanta.com\r\n")
        append("CSeq: 314159 INVITE\r\n")
        append("Contact: <sip:bob@biloxi.com>\r\n")
        append("Content-Length: 0\r\n\r\n")
    }
}

/**
 * Диапазоны Cloudflare (для LTE-режима: в туннель пускаем только их).
 * Список публичный: https://www.cloudflare.com/ips-v4 и /ips-v6.
 */
object CloudflareRanges {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Резервный список (публичные диапазоны Cloudflare) — используется, если сеть недоступна. */
    val FALLBACK: List<String> = listOf(
        "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
        "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
        "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
        "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
        "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
        "2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32",
    )

    suspend fun fetch(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val v4 = fetchLines("https://www.cloudflare.com/ips-v4")
            val v6 = fetchLines("https://www.cloudflare.com/ips-v6")
            (v4 + v6).filter { it.isNotBlank() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: FALLBACK
    }

    private fun fetchLines(url: String): List<String> {
        val request = Request.Builder().url(url).header("User-Agent", "Svoboda2-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            return response.body?.string().orEmpty().lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains('/') }
        }
    }
}
