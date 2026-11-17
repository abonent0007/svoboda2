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

    private val meta = listOf(
        PublicServer("nl_1", "Нидерланды · 1010", "NL", "nl.tribukvy.ltd", 1010, "public_servers/nl_1.conf"),
        PublicServer("nl_2", "Нидерланды · 878", "NL", "nl.tribukvy.ltd", 878, "public_servers/nl_2.conf"),
        PublicServer("nl_3", "Нидерланды · 891", "NL", "nl.tribukvy.ltd", 891, "public_servers/nl_3.conf"),
        PublicServer("nl_4", "Нидерланды · 3476 (nl2)", "NL", "nl2.tribukvy.ltd", 3476, "public_servers/nl_4.conf"),
        PublicServer("nl_5", "Нидерланды · 880 (nl2)", "NL", "nl2.tribukvy.ltd", 880, "public_servers/nl_5.conf"),
        PublicServer("nl_6", "Нидерланды · 955 (nl2)", "NL", "nl2.tribukvy.ltd", 955, "public_servers/nl_6.conf"),
        PublicServer("nl_7", "Нидерланды · 3138 (nl3)", "NL", "nl3.tribukvy.ltd", 3138, "public_servers/nl_7.conf"),
        PublicServer("nl_8", "Нидерланды · 928 (nl3)", "NL", "nl3.tribukvy.ltd", 928, "public_servers/nl_8.conf"),
        PublicServer("nl_9", "Нидерланды · 942 (nl3)", "NL", "nl3.tribukvy.ltd", 942, "public_servers/nl_9.conf"),
        PublicServer("fi_1", "Финляндия · 1843", "FI", "fi.tribukvy.ltd", 1843, "public_servers/fi_1.conf"),
        PublicServer("fi_2", "Финляндия · 864", "FI", "fi.tribukvy.ltd", 864, "public_servers/fi_2.conf"),
        PublicServer("fi_3", "Финляндия · 934", "FI", "fi.tribukvy.ltd", 934, "public_servers/fi_3.conf"),
        PublicServer("any_1", "США/Anycast · 3138", "US", "engage.cloudflareclient.com", 3138, "public_servers/any_1.conf"),
        PublicServer("any_2", "США/Anycast · 7156", "US", "engage.cloudflareclient.com", 7156, "public_servers/any_2.conf"),
        PublicServer("any_3", "США/Anycast · 903", "US", "engage.cloudflareclient.com", 903, "public_servers/any_3.conf"),
    )

    val servers: List<PublicServer> = meta

    fun byId(id: String): PublicServer? = servers.firstOrNull { it.id == id }

    fun config(context: Context, id: String): String? = runCatching {
        val asset = byId(id)?.confAsset ?: return@runCatching null
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }.getOrNull()

    const val ATTRIBUTION = "Публичные конфиги: Delta-Kronecker/Cloudflare-Warp (публично опубликованы)"
}
