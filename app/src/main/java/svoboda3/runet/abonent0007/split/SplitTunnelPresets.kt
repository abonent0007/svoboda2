package svoboda3.runet.abonent0007.split

/**
 * Пресеты приложений для split-tunnel.
 * Списки package name версионируемые; пользователь подтверждает состав.
 */
object SplitTunnelPresets {
    data class Preset(val id: String, val title: String, val packages: Set<String>)

    val russianApps = Preset(
        id = "russian_apps",
        title = "Российские приложения",
        packages = setOf(
            "ru.yandex.searchplugin", "com.yandex.browser", "ru.yandex.yandexmaps",
            "ru.yandex.taxi", "ru.yandex.market", "ru.yandex.disk",
            "ru.mail.mailapp", "ru.sberbankmobile", "ru.tinkoff.invest",
            "ru.vtb.mobile", "com.idamob.tinkoff.android", "ru.alfabank.mobile.android",
            "ru.gazprombank.android", "ru.raiffeisennews", "ru.ozon.app.android",
            "com.wildberries.ru", "ru.lamoda.app", "ru.ivi.client",
            "ru.kinopoisk", "ru.ok.android", "com.vkontakte.android",
            "ru.zen.android", "ru.rutube.app", "ru.dnevnik.app",
            "ru.gosuslugi.android", "ru.mos.app", "com.sberbank.spasibo",
        ),
    )

    val messengers = Preset(
        id = "messengers",
        title = "Мессенджеры",
        packages = setOf(
            "org.telegram.messenger", "com.whatsapp", "com.viber.voip",
            "com.discord", "com.skype.raider", "com.facebook.orca",
        ),
    )

    val browsers = Preset(
        id = "browsers",
        title = "Браузеры",
        packages = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
            "com.microsoft.emmx", "com.brave.browser", "com.sec.android.app.sbrowser",
        ),
    )

    val all = listOf(russianApps, messengers, browsers)
}
