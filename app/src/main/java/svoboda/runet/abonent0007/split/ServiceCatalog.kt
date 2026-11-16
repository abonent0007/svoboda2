package svoboda.runet.abonent0007.split

import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Каталог сервисов для раздельного туннелирования.
 *
 * Список составлен по аналогии с публичными WARP-генераторами: YouTube, мессенджеры,
 * соцсети и RU-сервисы, которые часто нужны «напрямую» (минуя туннель).
 *
 * Пакеты определяются по названию (label) установленного приложения — так мы не зависим
 * от конкретных версий и не ломаемся на пакетах, которых нет на устройстве.
 */
object ServiceCatalog {

    data class Service(val id: String, val title: String, val labels: List<String>)

    val services: List<Service> = listOf(
        Service("youtube", "YouTube", listOf("YouTube")),
        Service("discord", "Discord", listOf("Discord")),
        Service("whatsapp", "WhatsApp", listOf("WhatsApp")),
        Service("telegram", "Telegram", listOf("Telegram", "Telegram X")),
        Service("twitter", "Twitter / X", listOf("Twitter", "X")),
        Service("instagram", "Instagram", listOf("Instagram")),
        Service("facebook", "Facebook", listOf("Facebook")),
        Service("viber", "Viber", listOf("Viber")),
        Service("itch", "Itch.io", listOf("itch.io", "Itch.io")),
        Service("rutracker", "RuTracker", listOf("RuTracker", "Рутрекер")),
        Service("rutor", "Rutor", listOf("Rutor", "Рутор")),
        Service("nnmclub", "NNMClub", listOf("NNM-Club", "NNMClub")),
        Service("bestchange", "BestChange", listOf("BestChange")),
        Service("animego", "AnimeGo", listOf("AnimeGo")),
        Service("hdrezka", "HDrezka", listOf("HDrezka", "HDRezka")),
    )

    fun byId(id: String): Service? = services.firstOrNull { it.id == id }

    /**
     * Сопоставляет сервисы с установленными пакетами.
     * Возвращает map: serviceId → packageNames (только реально установленные).
     */
    fun resolve(context: Context): Map<String, Set<String>> {
        val pm = context.packageManager
        val installed = runCatching {
            pm.getInstalledApplications(0).map { app ->
                val label = runCatching {
                    pm.getApplicationLabel(app).toString().trim()
                }.getOrNull().orEmpty()
                app.packageName to label
            }
        }.getOrDefault(emptyList())

        val result = HashMap<String, Set<String>>()
        services.forEach { service ->
            val packages = installed.filter { (_, label) ->
                label.isNotBlank() && service.labels.any { target ->
                    label.equals(target, ignoreCase = true) ||
                        label.lowercase(Locale.US).startsWith(target.lowercase(Locale.US))
                }
            }.map { it.first }.toSet()
            if (packages.isNotEmpty()) result[service.id] = packages
        }
        return result
    }

    /** Пакеты для набора выбранных сервисов (только установленные). */
    fun packagesFor(context: Context, serviceIds: Set<String>): Set<String> {
        val resolved = resolve(context)
        return serviceIds.flatMap { resolved[it].orEmpty() }.toSet()
    }

    /** Все установленные пакеты выбранных сервисов — даже если сервис не найден по label. */
    fun fallbackPackages(serviceIds: Set<String>): Set<String> {
        val known = mapOf(
            "youtube" to setOf("com.google.android.youtube"),
            "discord" to setOf("com.discord"),
            "whatsapp" to setOf("com.whatsapp"),
            "telegram" to setOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "twitter" to setOf("com.twitter.android"),
            "instagram" to setOf("com.instagram.android"),
            "facebook" to setOf("com.facebook.orca", "com.facebook.katana"),
            "viber" to setOf("com.viber.voip"),
            "itch" to setOf("io.itch.monster", "com.itch.app"),
            "rutracker" to setOf("ru.rutracker"),
            "rutor" to setOf("ru.rutor"),
            "nnmclub" to setOf("nnmclub", "ru.nnmlub"),
            "bestchange" to setOf("com.bestchange", "ru.bestchange"),
            "animego" to setOf("ru.animego"),
            "hdrezka" to setOf("com.hdrezka", "ru.hdrezka"),
        )
        return serviceIds.flatMap { known[it].orEmpty() }.toSet()
    }
}
