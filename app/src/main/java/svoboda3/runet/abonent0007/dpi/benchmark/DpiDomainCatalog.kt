package svoboda3.runet.abonent0007.dpi.benchmark

/**
 * Каталог доменов для бенчмарка DPI-стратегий.
 *
 * ТЗ: 7 категорий, для каждого домена — название, адрес и ожидаемый результат.
 * httpOptional = true для CDN-доменов, где TLS-рукопожатие само по себе
 * доказывает работоспособность маршрута, а HTTP-ответ не гарантирован.
 */
data class DpiTestDomain(
    val id: String,
    val domain: String,
    val title: String,
    val category: String,
    val testPath: String = "/",
    val expectedCodes: Set<Int> = setOf(200, 301, 302),
    val httpOptional: Boolean = false,
    val note: String? = null,
)

data class DpiDomainCategory(
    val id: String,
    val title: String,
    val domains: List<DpiTestDomain>,
)

object DpiDomainCatalog {

    private fun d(
        id: String,
        domain: String,
        title: String,
        category: String,
        path: String = "/",
        codes: Set<Int> = setOf(200, 301, 302),
        httpOptional: Boolean = false,
        note: String? = null,
    ) = DpiTestDomain(id, domain, title, category, path, codes, httpOptional, note)

    /** Основные сайты и проверка HTTPS. */
    val basics = listOf(
        d("google.com", "google.com", "Google", "Базовые"),
        d("www.google.com", "www.google.com", "Google Web", "Базовые"),
        d("ya.ru", "ya.ru", "Яндекс", "Базовые"),
        d("yandex.ru", "yandex.ru", "Яндекс (зона .ru)", "Базовые"),
        d("cloudflare.com", "cloudflare.com", "Cloudflare", "Базовые"),
        d("www.cloudflare.com", "www.cloudflare.com", "Cloudflare CDN", "Базовые"),
        d("example.com", "example.com", "Контрольный сайт", "Базовые"),
        d("wikipedia.org", "wikipedia.org", "Независимый HTTPS", "Базовые"),
    )

    /** Видеосервисы. */
    val video = listOf(
        d("youtube.com", "youtube.com", "YouTube", "Видео"),
        d("www.youtube.com", "www.youtube.com", "YouTube — интерфейс", "Видео"),
        d("m.youtube.com", "m.youtube.com", "YouTube — мобильная версия", "Видео"),
        d("youtubei.googleapis.com", "youtubei.googleapis.com", "YouTube API", "Видео"),
        d("googlevideo.com", "googlevideo.com", "Видеодоставка", "Видео", httpOptional = true, note = "CDN: TLS без HTTP-ответа — норма"),
        d("ytimg.com", "ytimg.com", "Картинки YouTube", "Видео", httpOptional = true),
        d("i.ytimg.com", "i.ytimg.com", "Превью видео", "Видео", httpOptional = true),
    )

    /** Telegram. Не проверяет доступность DC по IP — только HTTPS-поверхность. */
    val telegram = listOf(
        d("telegram.org", "telegram.org", "Официальный сайт", "Telegram", note = "Не проверяет DC по IP"),
        d("web.telegram.org", "web.telegram.org", "Telegram Web", "Telegram", note = "Не проверяет DC по IP"),
        d("t.me", "t.me", "Ссылки t.me", "Telegram", note = "Не проверяет DC по IP"),
        d("telegram.me", "telegram.me", "Альтернативный домен", "Telegram", note = "Не проверяет DC по IP"),
        d("api.telegram.org", "api.telegram.org", "Telegram Bot API", "Telegram", note = "Не проверяет DC по IP"),
        d("core.telegram.org", "core.telegram.org", "Документация", "Telegram", note = "Не проверяет DC по IP"),
    )

    /** AI-сервисы. 403 от анти-бот защиты допустим (сеть и TLS работают). */
    val ai = listOf(
        d("chatgpt.com", "chatgpt.com", "ChatGPT", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("openai.com", "openai.com", "OpenAI", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("api.openai.com", "api.openai.com", "OpenAI API", "AI-сервисы", codes = setOf(200, 401, 403, 404)),
        d("gemini.google.com", "gemini.google.com", "Gemini", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("generativelanguage.googleapis.com", "generativelanguage.googleapis.com", "Gemini API", "AI-сервисы", codes = setOf(200, 401, 403, 404)),
        d("grok.com", "grok.com", "Grok", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("x.ai", "x.ai", "xAI", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("api.groq.com", "api.groq.com", "Groq API", "AI-сервисы", codes = setOf(200, 401, 403, 404)),
        d("openrouter.ai", "openrouter.ai", "OpenRouter", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
        d("opencode.ai", "opencode.ai", "OpenCode", "AI-сервисы", codes = setOf(200, 301, 302, 403)),
    )

    /** Разработка и репозитории. */
    val dev = listOf(
        d("github.com", "github.com", "GitHub", "Разработка"),
        d("api.github.com", "api.github.com", "GitHub API", "Разработка", codes = setOf(200, 401, 403)),
        d("raw.githubusercontent.com", "raw.githubusercontent.com", "Raw-исходники", "Разработка"),
        d("githubusercontent.com", "githubusercontent.com", "Контент GitHub", "Разработка", httpOptional = true),
        d("gitlab.com", "gitlab.com", "GitLab", "Разработка"),
        d("stackoverflow.com", "stackoverflow.com", "Stack Overflow", "Разработка"),
    )

    /** Соцсети и мессенджеры. */
    val social = listOf(
        d("discord.com", "discord.com", "Discord", "Соцсети"),
        d("discord.gg", "discord.gg", "Приглашения Discord", "Соцсети"),
        d("reddit.com", "reddit.com", "Reddit", "Соцсети"),
        d("www.reddit.com", "www.reddit.com", "Reddit Web", "Соцсети"),
        d("x.com", "x.com", "X", "Соцсети"),
        d("twitter.com", "twitter.com", "Twitter", "Соцсети"),
    )

    /** Дополнительные тестовые ресурсы. */
    val extra = listOf(
        d("speed.cloudflare.com", "speed.cloudflare.com", "Проверка скорости", "Дополнительные"),
        d("one.one.one.one", "one.one.one.one", "Cloudflare DoH", "Дополнительные"),
        d("dns.google", "dns.google", "Google DoH", "Дополнительные"),
    )

    val categories: List<DpiDomainCategory> = listOf(
        DpiDomainCategory("basics", "Базовые сайты", basics),
        DpiDomainCategory("video", "Видеосервисы", video),
        DpiDomainCategory("telegram", "Telegram", telegram),
        DpiDomainCategory("ai", "AI-сервисы", ai),
        DpiDomainCategory("dev", "Разработка", dev),
        DpiDomainCategory("social", "Соцсети", social),
        DpiDomainCategory("extra", "Дополнительные", extra),
    )

    val domains: List<DpiTestDomain> = categories.flatMap { it.domains }

    fun byId(id: String): DpiTestDomain? = domains.firstOrNull { it.id == id }

    /** Набор по умолчанию: компактный, но покрывающий разные механизмы блокировок. */
    val defaultSelection: Set<String> = setOf(
        "google.com", "ya.ru", "wikipedia.org",
        "www.youtube.com", "youtubei.googleapis.com",
        "web.telegram.org", "t.me",
        "chatgpt.com", "gemini.google.com", "api.groq.com", "opencode.ai",
        "github.com", "raw.githubusercontent.com",
        "discord.com", "x.com",
        "speed.cloudflare.com",
    )
}
