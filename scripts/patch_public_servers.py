import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main'

# 1) Prefs: карта измеренных стран для публичных серверов
p = root + r'\java\svoboda\runet\abonent0007\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    /** Предыдущая пользовательская DPI-стратегия (для отката). */"""
addition = """    /** Измеренные страны выхода публичных серверов ("id=CC"). */
    var publicServerCountries: Map<String, String>
        get() = (prefs.getStringSet("public_server_countries", emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        set(value) = prefs.edit().putStringSet("public_server_countries", value.map { "${it.key}=${it.value}" }.toSet()).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: publicServerCountries')

# 2) ConnectionManager: подключение к публичному серверу + учёт стран в AUTO
p = root + r'\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

# импорт PublicServerCatalog
old_i = "import svoboda.runet.abonent0007.own.OwnServerStore\n"
new_i = "import svoboda.runet.abonent0007.own.PublicServerCatalog\n" + old_i
assert s.count(old_i) == 1
s = s.replace(old_i, new_i, 1)

# подключение к публичному серверу
old = """    /** Подключиться к конкретному сгенерированному WARP-профилю. */"""
new = """    /** Подключиться к встроенному публичному WARP-серверу; страну измеряем после подключения. */
    suspend fun connectPublicServer(id: String): Boolean {
        val server = PublicServerCatalog.byId(id) ?: return false
        val config = PublicServerCatalog.config(AppGraph.appContext, id) ?: return false
        controller.disconnect()
        controller.currentProfileId = "public:$id"
        applyServiceDirect(controller.currentProfileId)
        _profileLabel.value = "${server.title} (публичный)"
        val ok = controller.connect(config)
        if (ok) {
            val exit = NetProbe.fetchExit()
            val cc = exit?.country?.uppercase().orEmpty()
            if (cc.isNotBlank()) {
                val map = prefs.publicServerCountries.toMutableMap()
                map[id] = cc
                prefs.publicServerCountries = map
            }
        }
        return ok
    }

    /** Подключиться к конкретному сгенерированному WARP-профилю. */"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# AUTO: публичные NL/FI/US — кандидаты между WARP-профилями и DPI
old = """                "WARP" -> {
                    val profiles = store.list()
                    countries.forEach { cc ->
                        profiles.filter { it.observedCountry.trim().uppercase() == cc }.forEach { prof ->
                            candidates += { connectGeneratedProfile(prof) }
                        }
                    }
                    candidates += { connectWarpWithGeneration() }
                }"""
new = """                "WARP" -> {
                    val profiles = store.list()
                    countries.forEach { cc ->
                        profiles.filter { it.observedCountry.trim().uppercase() == cc }.forEach { prof ->
                            candidates += { connectGeneratedProfile(prof) }
                        }
                    }
                    // Встроенные публичные NL/FI/US: приоритет — по измеренной стране
                    // (без измерения идём в порядке метки: NL, FI, затем любые).
                    val public = PublicServerCatalog.servers
                    val measured = prefs.publicServerCountries
                    val priorityIndex: (String) -> Int = { cc ->
                        countries.indexOf(cc.trim().uppercase()).let { index -> if (index < 0) 99 else index }
                    }
                    public.sortedBy { priorityIndex(measured[it.id] ?: it.countryCode) }.forEach { pub ->
                        val matchesCountry = countries.contains(measured[pub.id]?.uppercase() ?: pub.countryCode.uppercase())
                        if (matchesCountry || !strict) {
                            candidates += { connectPublicServer(pub.id) }
                        }
                    }
                    candidates += { connectWarpWithGeneration() }
                }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: public servers + AUTO')

# 3) UI: секция публичных серверов в AWG-разделе
p = root + r'\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()
old = """    var showBenchmark by remember { mutableStateOf(false) }"""
new = old + "\n    val publicServers = svoboda.runet.abonent0007.own.PublicServerCatalog.servers\n    val publicCountries = remember { AppGraph.prefs.publicServerCountries }"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """    SectionTitle("DNS В КОНФИГЕ")"""
new = """    SectionTitle("ПУБЛИЧНЫЕ СЕРВЕРЫ · NL/FI/США")
    Text(
        text = "Публично опубликованные WARP-конфиги (Delta-Kronecker/Cloudflare-Warp), " +
            "страна в имени хоста. Страна выхода измеряется после подключения и отображается рядом. " +
            "Доступность не гарантирована — это публичные коллективные identity.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    publicServers.chunked(3).forEach { rowServers ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rowServers.forEach { server ->
                val country = publicCountries[server.id]
                TextButton(onClick = {
                    scope.launch {
                        profileStatus = "Подключение ${server.host}:${server.port} …"
                        val ok = AppGraph.manager.connectPublicServer(server.id)
                        profileStatus = if (ok) {
                            "Подключено: ${server.title}"
                        } else {
                            "Не удалось подключить ${server.host}"
                        }
                    }
                }) {
                    Text(
                        text = server.title + (country?.let { " · $it" } ?: ""),
                        fontSize = 11.sp,
                        color = AccentGreen,
                    )
                }
            }
        }
    }

""" + old
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AWG UI: public servers section')
