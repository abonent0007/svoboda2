p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\WarpGenerator.kt'
s = open(p, encoding='utf-8').read()

# buildConfig: optional DNS
old = """    fun buildConfig(
        identity: WarpIdentity,
        endpointHost: String,
        port: Int,
        jc: Int = 7,
        jmin: Int = 54,
        jmax: Int = 76,
        i1: String = "",
    ): String = buildString {"""
new = """    fun buildConfig(
        identity: WarpIdentity,
        endpointHost: String,
        port: Int,
        jc: Int = 7,
        jmin: Int = 54,
        jmax: Int = 76,
        i1: String = "",
        dns: String = DNS_CLOUDFLARE,
    ): String = buildString {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        appendLine("DNS = 1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001")"""
new = """        if (dns.isNotBlank()) appendLine("DNS = $dns")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# DNS presets
old = "    const val MTU = 1420\n"
new = """    const val MTU = 1420

    /** Пресеты DNS для генерируемых конфигов. */
    const val DNS_CLOUDFLARE = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001"
    const val DNS_GOOGLE = "8.8.8.8, 8.8.4.4, 2001:4860:4860::8888, 2001:4860:4860::8844"
    const val DNS_QUAD9 = "9.9.9.9, 149.112.112.112, 2620:fe::fe, 2620:fe::9"
    const val DNS_NONE = ""

    /** Пресеты DNS для UI: id → (название, значение). */
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
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpGenerator: DNS presets added')

# WarpProfileFactory: wider endpoint pool + dns param
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\WarpProfileFactory.kt'
s = open(p, encoding='utf-8').read()

old = """    val KNOWN_ENDPOINTS: List<Endpoint> = listOf(
        Endpoint("162.159.192.1", 2408),
        Endpoint("162.159.193.1", 2408),
        Endpoint("162.159.195.1", 2408),
        Endpoint("162.159.192.100", 2408),
        Endpoint("162.159.193.100", 2408),
        Endpoint("162.159.195.100", 2408),
        Endpoint("162.159.192.225", 2408),
        Endpoint("162.159.193.225", 2408),
        Endpoint("162.159.195.225", 2408),
        Endpoint("162.159.192.1", 500),
        Endpoint("162.159.193.10", 2408),
        Endpoint("162.159.192.1", 1701),
        Endpoint("162.159.193.10", 4500),
    )"""
new = """    val KNOWN_ENDPOINTS: List<Endpoint> = buildList {
        // Публичные anycast-подсети WARP (Cloudflare): 162.159.192/193/195.0/24 и 188.114.96-99.0/24.
        val subnets = listOf(
            "162.159.192", "162.159.193", "162.159.195",
            "188.114.96", "188.114.97", "188.114.98", "188.114.99",
        )
        val hosts = listOf(1, 10, 100, 225)
        subnets.forEach { net ->
            hosts.forEach { h -> add(Endpoint("$net.$h", 2408)) }
        }
        // Стандартные WG-порты (принимаются не всеми сетями — как вариант).
        add(Endpoint("162.159.192.1", 500))
        add(Endpoint("162.159.193.10", 1701))
        add(Endpoint("162.159.195.1", 4500))
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """    suspend fun generate(
        count: Int,
        identity: WarpIdentity?,
        seedRawConfig: String,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {"""
new = """    suspend fun generate(
        count: Int,
        identity: WarpIdentity?,
        seedRawConfig: String,
        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                jmax = jmax,
                i1 = i1,
            )"""
new = """                jmax = jmax,
                i1 = i1,
                dns = dns,
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpProfileFactory: endpoint pool + dns')
