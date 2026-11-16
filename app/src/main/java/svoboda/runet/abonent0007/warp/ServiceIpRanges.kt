package svoboda.runet.abonent0007.warp

import android.content.Context
import org.json.JSONObject

/**
 * Диапазоны IP по сервисам — для «раздельного туннелирования» на уровне маршрутов.
 *
 * Списки взяты из проекта nellimonix/warp-config-generator-vercel (MIT License,
 * © 2025 nellimonix) — публичные данные об IP-диапазонах сервисов.
 *
 * Механика: в конфиге AmneziaWG AllowedApps указываются только эти подсети →
 * в туннель попадает трафик выбранных сервисов, остальное идёт напрямую
 * (наш GoBackend добавляет маршруты именно из AllowedIPs и включает
 * allowFamily для остального — «kill-switch semantics» наоборот).
 */
object ServiceIpRanges {

    data class Service(val id: String, val title: String)

    /** Каталог сервисов с диапазонами (id → название). */
    val catalog: List<Service> = listOf(
        Service("youtube", "YouTube"),
        Service("telegram", "Telegram"),
        Service("discord", "Discord"),
        Service("whatsapp", "WhatsApp"),
        Service("twitter", "Twitter / X"),
        Service("instagram", "Instagram"),
        Service("facebook", "Facebook"),
        Service("viber", "Viber"),
        Service("itchio", "Itch.io"),
        Service("rutracker", "RuTracker"),
        Service("rutor", "Rutor"),
        Service("nnmclub", "NNMClub"),
        Service("bestchange", "BestChange"),
        Service("animego", "AnimeGo"),
        Service("signal", "Signal"),
        Service("proton", "Proton"),
    )

    private val cache = HashMap<String, List<String>>()

    /** Диапазоны сервиса (CIDR), пусто если список недоступен. */
    fun ranges(context: Context, serviceId: String): List<String> {
        cache[serviceId]?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open("services/$serviceId.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            json.optString("ips")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains('/') }
        }.getOrDefault(emptyList())
        cache[serviceId] = parsed
        return parsed
    }

    /** Объединение диапазонов выбранных сервисов (без дублей). */
    fun union(context: Context, serviceIds: Set<String>): List<String> {
        val out = LinkedHashSet<String>()
        serviceIds.forEach { out.addAll(ranges(context, it)) }
        return out.toList()
    }

    /** Значение AllowedIPs для конфига: null → весь трафик (0.0.0.0/0, ::/0). */
    fun allowedIps(context: Context, serviceIds: Set<String>): String? {
        if (serviceIds.isEmpty()) return null
        val list = union(context, serviceIds)
        if (list.isEmpty()) return null
        return list.joinToString(", ")
    }

    /** Названия сервисов, для которых реально нашлись диапазоны. */
    fun resolvedTitles(context: Context, serviceIds: Set<String>): List<String> =
        catalog.filter { serviceIds.contains(it.id) && ranges(context, it.id).isNotEmpty() }.map { it.title }
}
