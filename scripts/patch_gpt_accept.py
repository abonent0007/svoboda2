import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# ---------- Prefs: autoOptIn public servers + freshness format ----------
p = root + r'\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = "    /** Измеренные страны выхода публичных серверов (\"id=CC\"). */"
addition = """    /** Разрешить публичные (сторонние) серверы в AUTO (по умолчанию выключено). */
    var autoPublicAllowed: Boolean
        get() = prefs.getBoolean("auto_public_allowed", false)
        set(value) = prefs.edit().putBoolean("auto_public_allowed", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)

# map entries may be "id=CC" or "id=CC@epoch"
old = """    var publicServerCountries: Map<String, String>
        get() = (prefs.getStringSet("public_server_countries", emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        set(value) = prefs.edit().putStringSet("public_server_countries", value.map { "${it.key}=${it.value}" }.toSet()).apply()"""
new = """    var publicServerCountries: Map<String, String>
        get() = (prefs.getStringSet("public_server_countries", emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        set(value) = prefs.edit().putStringSet("public_server_countries", value.map { "${it.key}=${it.value}" }.toSet()).apply()

    /** Возраст последнего измерения страны публичного сервера в днях (-1 если нет). */
    fun publicServerCountryAgeDays(id: String): Long {
        val raw = prefs.getStringSet("public_server_countries", emptySet())
            ?.firstOrNull { it.startsWith("$id=") || it == id }
            ?.substringAfter('=', "")
        ?: return -1
        val parts = raw.split('@', limit = 2)
        val ts = parts.getOrNull(1)?.toLongOrNull() ?: return Long.MAX_VALUE
        return (System.currentTimeMillis() - ts) / (24L * 60L * 60L * 1000L)
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: autoPublicAllowed + country age')

# ---------- ConnectionManager: fresh country + AUTO public gating + event log ----------
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

# auto log storage
old = "    /** Время последнего автоматического переключения (антидребезг). */\n    private var lastAutoSwitchAt: Long = 0L"
new = """    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L

    /** Журнал решений AUTO (одной строкой на событие). */
    @Volatile
    var autoLog: List<String> = loadAutoLog()
        private set

    private fun logAuto(event: String) {
        val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val line = "$stamp $event"
        autoLog = (autoLog + line).takeLast(150)
        runCatching {
            java.io.File(AppGraph.appContext.filesDir, "auto_log.txt").appendText(line + "\\n", Charsets.UTF_8)
        }
    }

    private fun loadAutoLog(): List<String> = runCatching {
        java.io.File(AppGraph.appContext.filesDir, "auto_log.txt").let { file ->
            if (file.exists()) file.readLines(Charsets.UTF_8).takeLast(150) else emptyList()
        }
    }.getOrDefault(emptyList())"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# measure freshness: warp profile + public server
old = """        if (prefs.autoHoldMinutes > 0 && controller.isConnected &&
            now - lastAutoSwitchAt < prefs.autoHoldMinutes * 60_000L
        ) {
            return true
        }"""
new = """        if (prefs.autoHoldMinutes > 0 && controller.isConnected &&
            now - lastAutoSwitchAt < prefs.autoHoldMinutes * 60_000L
        ) {
            logAuto("anti-bounce: удержание текущего транспорта ${prefs.autoHoldMinutes} мин")
            return true
        }
        logAuto("AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# AWG candidates: log
old = """                "AWG" -> {
                    val servers = serverStore.list()"""
new = """                "AWG" -> {
                    val servers = serverStore.list()
                    logAuto("AWG: своих серверов ${servers.size}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# WARP: gate public servers by autoPublicAllowed + fresh measurement
old = """                    // Встроенные публичные NL/FI/US: приоритет — по измеренной стране
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
                    }"""
new = """                    // Публичные (сторонние) NL/FI/US: только по явному согласию пользователя
                    // и только по СВЕЖЕЙ измеренной стране (иначе метка = "не подтверждён").
                    if (prefs.autoPublicAllowed) {
                        val publicServers = PublicServerCatalog.servers
                        val measured = prefs.publicServerCountries
                        val countryOf: (String) -> String = { id ->
                            val raw = measured[id] ?: ""
                            if (raw.isBlank()) "" else raw.substringBefore('@')
                        }
                        val priorityIndex: (String) -> Int = { cc ->
                            countries.indexOf(cc.trim().uppercase()).let { index -> if (index < 0) 99 else index }
                        }
                        val allowed = publicServers.filter { pub ->
                            val cc = countryOf(pub.id).uppercase()
                            val age = prefs.publicServerCountryAgeDays(pub.id)
                            // свежесть: измерение не старше 7 дней считается подтверждённым
                            val fresh = age in 0..7
                            if (fresh && countries.contains(cc)) return@filter true
                            if (!strict) true else false
                        }
                        allowed.sortedBy { priorityIndex(countryOf(it.id)) }.forEach { pub ->
                            candidates += { connectPublicServer(pub.id) }
                        }
                        if (allowed.isEmpty()) logAuto("публичные серверы отклонены (согласия нет или страна не свежая)")
                    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# strict empty message + log
old = """        if (strict && candidates.isEmpty()) {
            _profileLabel.value = "AUTO · нет профиля нужной страны"
            return false
        }"""
new = """        if (strict && candidates.isEmpty()) {
            logAuto("строгая страна: кандидатов нет — отказ")
            _profileLabel.value = "AUTO · нет профиля нужной страны"
            return false
        }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# connectPublicServer: сохраняет свежую метку времени измерения
old = """            if (cc.isNotBlank()) {
                val map = prefs.publicServerCountries.toMutableMap()
                map[id] = cc
                prefs.publicServerCountries = map
            }"""
new = """            if (cc.isNotBlank()) {
                val map = prefs.publicServerCountries.toMutableMap()
                map[id] = cc + "@" + System.currentTimeMillis()
                prefs.publicServerCountries = map
            }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# warp public candidates need context binding for lambda: done via connectPublicServer(pub.id)
old = """                "DPI" -> {
                    candidates += {
                        controller.currentProfileId = "dpi"
                        _profileLabel.value = "AUTO · DPI"
                        AppGraph.dpi.start()
                    }
                }"""
new = """                "DPI" -> {
                    candidates += {
                        logAuto("DPI кандидат")
                        controller.currentProfileId = "dpi"
                        _profileLabel.value = "AUTO · DPI"
                        AppGraph.dpi.start()
                    }
                }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# log selected transport
old = """        for (candidate in candidates) {
            if (!candidate()) continue
            val isDpi = controller.currentProfileId == "dpi""""
new = """        for ((i, candidate) in candidates.withIndex()) {
            logAuto("пробуем кандидата #${i + 1}")
            if (!candidate()) continue
            logAuto("кандидат #${i + 1} выбран: ${controller.currentProfileId}")
            val isDpi = controller.currentProfileId == "dpi""""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# Telegram DC fallback logging
old = """                if (!dc.reachable) {
                    autoStatus = "Telegram DC недоступны через DPI (${dc.error ?: "нет ответа"}) — ищем AWG/WARP"
                    if (!dpiFallback) {
                        dpiFallback = true
                        continue
                    }
                } else {
                    autoStatus = "Telegram DC доступны"
                }"""
new = """                if (!dc.reachable) {
                    logAuto("Telegram DC через DPI недоступны (${dc.error ?: "нет ответа"}) → ищем AWG/WARP")
                    autoStatus = "Telegram DC недоступны через DPI (${dc.error ?: "нет ответа"}) — ищем AWG/WARP"
                    if (!dpiFallback) {
                        dpiFallback = true
                        continue
                    }
                } else {
                    logAuto("Telegram DC доступны")
                    autoStatus = "Telegram DC доступны"
                }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        if (dpiFallback) {
            autoStatus = "Telegram DC недоступны: подключён DPI (AWG/WARP не найден)"
        }"""
new = """        if (dpiFallback) {
            logAuto("Telegram DC недоступны: подключён DPI (AWG/WARP не найден)")
            autoStatus = "Telegram DC недоступны: подключён DPI (AWG/WARP не найден)"
        }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# log country result after selection
old = """                lastAutoSwitchAt = System.currentTimeMillis()
                return true"""
new = """                lastAutoSwitchAt = System.currentTimeMillis()
                logAuto("переключение завершено: профиль=${controller.currentProfileId}")
                return true"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: AUTO log + public gate + freshness')

# ---------- PublicServerCatalog: anycast без ложной страны US ----------
p = root + r'\own\PublicServerCatalog.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('"any_1", "США/Anycast · 3138", "US"', '"any_1", "Anycast · страна после измерения · 3138", "US"')
s = s.replace('"any_2", "США/Anycast · 7156", "US"', '"any_2", "Anycast · страна после измерения · 7156", "US"')
s = s.replace('"any_3", "США/Anycast · 903", "US"', '"any_3", "Anycast · страна после измерения · 903", "US"')
open(p, 'w', encoding='utf-8').write(s)
print('PublicServerCatalog: no false US label')
