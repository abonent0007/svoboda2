package svoboda.runet.abonent0007.split

/**
 * Каталог российских приложений и веб-ресурсов, которым нужен **прямой** маршрут
 * (без VPN): банки, госуслуги, локальные сервисы. Некоторые из них блокируют
 * запросы с зарубежных/VPN-адресов, поэтому их трафик исключается из туннеля.
 *
 * Пакеты сгруппированы по категориям; в UI показываются только установленные.
 */
object RussianDirectCatalog {

    data class Category(val id: String, val title: String, val packages: Set<String>)

    /** Банки, платёжные и финансовые приложения. */
    val banks = Category(
        id = "banks",
        title = "Банки и платежи",
        packages = setOf(
            "ru.sberbankmobile",                 // Сбербанк
            "com.idamob.tinkoff.android",        // Т-Банк (Тинькофф)
            "ru.tinkoff.investing",              // Т-Инвестиции
            "ru.tinkoff.mb",                     // Т-Банк (legacy)
            "ru.alfabank.mobile.android",        // Альфа-Банк
            "ru.vtb24.mobilebanking.android",    // ВТБ
            "ru.vtb.mobile",                     // ВТБ (legacy)
            "ru.gazprombank.android.mobilebank", // Газпромбанк
            "ru.raiffeisennews",                 // Райффайзен
            "ru.sovcombank.mobile",              // Совкомбанк
            "ru.rosbank.app",                    // Росбанк
            "ru.psbank.mobile",                  // ПСБ
            "ru.open.mb",                        // Открытие
            "ru.pochta.bank",                    // Почта Банк
            "ru.mts.bank",                       // МТС Банк
            "com.sberbank.spasibo",              // СберСпасибо
            "ru.yandex.money",                   // ЮMoney
        ),
    )

    /** Госуслуги, налоги, городские сервисы, электронные ключи. */
    val government = Category(
        id = "gov",
        title = "Госуслуги и госучреждения",
        packages = setOf(
            "ru.gosuslugi.android",              // Госуслуги
            "ru.gosuslugi.goskey",               // Госключ
            "ru.gosuslugi.auto",                 // Госуслуги Авто
            "ru.fns.lkfl",                       // ФНС / Личный кабинет
            "ru.fns.chek",                       // ФНС (чеки)
            "ru.mos.app",                        // mos.ru
            "ru.mosmetro.metro",                 // Метро Москвы
            "ru.mos.parking",                    // Парковки Москвы
            "ru.emias.mobile",                   // ЕМИАС
            "ru.rt.atv.ultimate",                // РТ (гос. ТВ-сервисы)
            "com.octopod.russianpost.client.android", // Почта России
            "ru.tele2.mytele2",                  // Tele2 (оператор)
        ),
    )

    /** Российские сервисы и приложения (GAME: исключение целых приложений, не доменов). */
    val web = Category(
        id = "web",
        title = "Российские сервисы и приложения",
        packages = setOf(
            "ru.yandex.searchplugin",
            "com.yandex.browser",
            "ru.yandex.yandexmaps",
            "ru.yandex.yandexnavi",
            "ru.yandex.taxi",
            "ru.yandex.market",
            "ru.yandex.music",
            "ru.yandex.disk",
            "ru.kinopoisk",
            "ru.ivi.client",
            "com.vkontakte.android",
            "ru.ok.android",
            "ru.mail.mailapp",
            "ru.ozon.app.android",
            "com.wildberries.ru",
            "com.avito.android",
            "ru.lamoda.app",
            "ru.dns.shop.android",
            "ru.dodopizza.app",
            "ru.vkusvill",
        ),
    )

    val all: List<Category> = listOf(banks, government, web)
    val allPackages: Set<String> = all.flatMap { it.packages }.toSet()
}
