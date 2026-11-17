package svoboda.runet.abonent0007.own

import android.content.Context

// Встроенные публичные WARP-конфиги.
// Источник: Delta-Kronecker/Cloudflare-Warp — публично опубликованные коллективные
// конфиги для свободного использования (как публичные WARP-сиды, но больше и свежее).
// nl.* и fi.* носят код страны в имени хоста; engage.*=anycast, страна измеряется.
// Страна выхода измеряется после подключения и хранится в prefs отдельно.
object PublicServerCatalog {

    data class PublicServer(
        val id: String,
        val title: String,
        val countryCode: String,
        val host: String,
        val port: Int,
        val confAsset: String,
    )

    // IP вписаны на дату 2026-11-17 (публичные хосты nl2/nl3 — мёртвые, NXDOMAIN;
    // nl.* жив, fi.* жив; engage.cloudflareclient.com — официальный endpoint WARP).
    private val meta = listOf(
        PublicServer("nl_1", "Нидерланды · 1010", "NL", "31.76.110.142", 1010, "public_servers/nl_1.conf"),
        PublicServer("nl_2", "Нидерланды · 878", "NL", "31.76.110.142", 878, "public_servers/nl_2.conf"),
        PublicServer("nl_3", "Нидерланды · 891", "NL", "31.76.110.142", 891, "public_servers/nl_3.conf"),
        PublicServer("fi_1", "Финляндия · 1843", "FI", "144.31.28.185", 1843, "public_servers/fi_1.conf"),
        PublicServer("fi_2", "Финляндия · 864", "FI", "144.31.28.185", 864, "public_servers/fi_2.conf"),
        PublicServer("fi_3", "Финляндия · 934", "FI", "144.31.28.185", 934, "public_servers/fi_3.conf"),
        PublicServer("any_1", "Anycast · страна после измерения · 3138", "US", "engage.cloudflareclient.com", 3138, "public_servers/any_1.conf"),
        PublicServer("any_2", "Anycast · страна после измерения · 7156", "US", "engage.cloudflareclient.com", 7156, "public_servers/any_2.conf"),
        PublicServer("any_3", "Anycast · страна после измерения · 903", "US", "engage.cloudflareclient.com", 903, "public_servers/any_3.conf"),
    )

    val servers: List<PublicServer> = meta

    fun byId(id: String): PublicServer? = servers.firstOrNull { it.id == id }

    fun config(context: Context, id: String): String? = runCatching {
        val asset = byId(id)?.confAsset ?: return@runCatching null
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }.getOrNull()

    const val ATTRIBUTION = "Публичные конфиги: Delta-Kronecker/Cloudflare-Warp (публично опубликованы)"
}
