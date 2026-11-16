p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\WarpGenerator.kt'
s = open(p, encoding='utf-8').read()

old = """    /** Пресеты DNS для UI: id → (название, значение). */
    val DNS_PRESETS: List<Pair<String, String>> = listOf(
        "cloudflare" to "Cloudflare (1.1.1.1)",
        "google" to "Google (8.8.8.8)",
        "quad9" to "Quad9 (9.9.9.9)",
        "none" to "Без DNS",
    )

    fun dnsPreset(id: String): String = when (id) {
        "google" -> DNS_GOOGLE
        "quad9" -> DNS_QUAD9
        "none" -> DNS_NONE
        else -> DNS_CLOUDFLARE
    }
"""
new = """    /**
     * Пресеты DNS: id → (название, тип, значение).
     * Тип "ip" — готовые адреса; "host" — домен резолвера (разрешается в IP при генерации);
     * "none" — без DNS (адреса берёт система).
     */
    data class DnsPreset(val id: String, val title: String, val kind: String, val value: String)

    val DNS_PRESETS: List<DnsPreset> = listOf(
        DnsPreset("cloudflare", "Cloudflare (1.1.1.1)", "ip", DNS_CLOUDFLARE),
        DnsPreset("google", "Google (8.8.8.8)", "ip", DNS_GOOGLE),
        DnsPreset("quad9", "Quad9 (9.9.9.9)", "ip", DNS_QUAD9),
        DnsPreset("malw", "Антиблок dns.malw.link", "host", "dns.malw.link"),
        DnsPreset("bezmezhau", "Антиблок bezmezhau.com", "host", "bezmezhau.com"),
        DnsPreset("geohide", "Антиблок geohide.ru", "host", "geohide.ru"),
        DnsPreset("geodema", "Антиблок dns.geodema.love", "host", "dns.geodema.love"),
        DnsPreset("none", "Без DNS (системный)", "none", DNS_NONE),
    )

    fun dnsPreset(id: String): DnsPreset = DNS_PRESETS.firstOrNull { it.id == id } ?: DNS_PRESETS.first()

    /**
     * Готовая строка "DNS = ..." для конфига.
     * Для антиблок-резолверов домен разрешается в IP: WireGuard/AmneziaWG принимает только адреса.
     */
    fun resolveDns(presetId: String): String {
        val preset = dnsPreset(presetId)
        return when (preset.kind) {
            "none" -> ""
            "host" -> runCatching {
                InetAddress.getAllByName(preset.value)
                    .map { it.hostAddress }
                    .filter { it.contains(':') || it.matches(Regex("\\\\d+\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+")) }
                    .distinct()
                    .joinToString(", ")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: DNS_CLOUDFLARE
            else -> preset.value
        }
    }
"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpGenerator: DNS presets v2 (with RU anti-block resolvers)')

# WarpProfileFactory: dns by preset id
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\WarpProfileFactory.kt'
s = open(p, encoding='utf-8').read()
old = """        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {"""
new = """        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {
        val dnsValue = dns"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
old = """                dns = dns,
            )"""
new = """                dns = dnsValue,
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpProfileFactory: dns value wired')
