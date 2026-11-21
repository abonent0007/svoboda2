package svoboda3.runet.abonent0007.warp

/**
 * DNS-провайдеры для генерируемых WARP-конфигов.
 *
 * Данные (IP-адреса) взяты из проекта nellimonix/warp-config-generator-vercel
 * (MIT License, © 2025 nellimonix) — свободное использование с сохранением
 * указания авторства. Состав отражает публичные резолверы:
 *  - обычные: Cloudflare / Google / Quad9;
 *  - «community» (антиблок): возвращают адреса, рассчитанные на обход блокировок,
 *    поэтому требуют, чтобы ВЕСЬ трафик шёл через туннель (при раздельном
 *    туннелировании прямой трафик получил бы «чужие» адреса).
 */
data class WarpDnsProvider(
    val id: String,
    val label: String,
    val ipv4: List<String>,
    val ipv6: List<String>,
    /** Community-резолверы запрещают раздельное туннелирование. */
    val isCommunity: Boolean,
)

object WarpDnsProviders {

    val providers: List<WarpDnsProvider> = listOf(
        WarpDnsProvider(
            "cf", "1.1.1.1",
            listOf("1.1.1.1", "1.0.0.1"),
            listOf("2606:4700:4700::1111", "2606:4700:4700::1001"),
            false,
        ),
        WarpDnsProvider(
            "google", "8.8.8.8",
            listOf("8.8.8.8", "8.8.4.4"),
            listOf("2001:4860:4860::8888", "2001:4860:4860::8844"),
            false,
        ),
        WarpDnsProvider(
            "quad9", "dns.quad9.net",
            listOf("9.9.9.9", "149.112.112.112"),
            listOf("2620:fe::fe", "2620:fe::9"),
            false,
        ),
        WarpDnsProvider(
            "malw", "dns.malw.link",
            listOf("95.216.204.218", "80.253.249.40"),
            listOf("2a01:4f9:c014:6dac::1", "2a12:bec4:1460:5b7::2"),
            true,
        ),
        WarpDnsProvider(
            "geohide", "geohide.ru",
            listOf("45.155.204.190", "37.230.192.51", "159.194.200.33", "193.233.112.67", "193.233.112.88"),
            emptyList(),
            true,
        ),
        WarpDnsProvider(
            "comss", "dns.comss.one",
            listOf("83.220.169.155", "212.109.195.93", "195.133.25.16"),
            listOf("2a01:230:4:915::2", "2a01:230:4:306::2"),
            true,
        ),
        WarpDnsProvider(
            "mafioznik", "dns.mafioznik.xyz",
            listOf("103.27.157.38", "103.27.157.100"),
            emptyList(),
            true,
        ),
    )

    const val DEFAULT_ID = "cf"

    fun byId(id: String): WarpDnsProvider = providers.firstOrNull { it.id == id } ?: providers.first()

    fun isCommunity(id: String): Boolean = byId(id).isCommunity

    /** Значение для строки "DNS = ..." (IPv6 отбрасываются при выключенном IPv6). */
    fun buildDnsLine(id: String, includeIpv6: Boolean): String {
        val p = byId(id)
        val ips = if (includeIpv6) p.ipv4 + p.ipv6 else p.ipv4
        return ips.joinToString(", ")
    }
}

/**
 * Endpoint'ы WARP.
 *
 * Данные — из проекта nellimonix/warp-config-generator-vercel (MIT, © 2025 nellimonix):
 * публичные anycast-точки и hostname Cloudflare (engage.cloudflareclient.com —
 * официальный endpoint WARP, SNI выбирается по имени хоста).
 *
 * Страновые «флаговые» релеи у того проекта — их собственная инфраструктура
 * (доступ по регистрации в Telegram-боте), мы их не используем: страна выхода
 * в WARP определяется ближайшим PoP Cloudflare, поэтому в приложении страна
 * измеряется по факту подключения.
 */
data class WarpEndpointOption(
    val id: String,
    val title: String,
    val host: String,
    val port: Int,
    val note: String? = null,
)

object WarpEndpoints {

    val options: List<WarpEndpointOption> = listOf(
        WarpEndpointOption("default", "Cloudflare anycast", "162.159.195.1", 500, "Классическая точка входа WARP"),
        WarpEndpointOption("engage2408", "Cloudflare (имя хоста, 2408)", "engage.cloudflareclient.com", 2408, "SNI выбирается по имени"),
        WarpEndpointOption("engage4500", "Cloudflare (имя хоста, 4500)", "engage.cloudflareclient.com", 4500, "SNI выбирается по имени"),
        WarpEndpointOption("fra1", "Cloudflare FRA #1", "188.114.97.66", 4500, "Париж, Франция"),
        WarpEndpointOption("fra2", "Cloudflare FRA #2", "188.114.96.125", 4500, "Париж, Франция"),
        WarpEndpointOption("pool", "Пул anycast-адресов", "", 2408, "Случайный адрес из публичных подсетей Cloudflare"),
    )

    /** Публичные подсети WARP/Cloudflare, из которых берём адреса для «пула». */
    val POOL_SUBNETS = listOf(
        "162.159.192", "162.159.193", "162.159.195",
        "188.114.96", "188.114.97", "188.114.98", "188.114.99",
    )

    val POOL_HOSTS = listOf(1, 10, 100, 225)

    fun byId(id: String): WarpEndpointOption? = options.firstOrNull { it.id == id }

    fun defaultOption(): WarpEndpointOption = options.first()

    /** Адрес из пула (детерминированно по индексу). */
    fun poolEndpoint(index: Int): WarpEndpointOption {
        val net = POOL_SUBNETS[index % POOL_SUBNETS.size]
        val host = POOL_HOSTS[(index / POOL_SUBNETS.size) % POOL_HOSTS.size]
        return WarpEndpointOption(
            id = "pool_${net}_$host",
            title = "$net.$host",
            host = "$net.$host",
            port = 2408,
        )
    }
}
