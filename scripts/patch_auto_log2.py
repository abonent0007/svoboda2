p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

# 1) autoLog system
old = """
    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L
"""
new = """
    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L

    /** Журнал решений AUTO (одной строкой на событие), хранится локально. */
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
        val file = java.io.File(AppGraph.appContext.filesDir, "auto_log.txt")
        if (file.exists()) file.readLines(Charsets.UTF_8).takeLast(150) else emptyList()
    }.getOrDefault(emptyList())
"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) anti-bounce + start logging
old = """        ) {
            return true
        }"""
new = """        ) {
            logAuto("anti-bounce: удержание ${prefs.autoHoldMinutes} мин")
            return true
        }
        logAuto("AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 3) AWG candidates count
old = """                "AWG" -> {
                    val servers = serverStore.list()"""
new = """                "AWG" -> {
                    val servers = serverStore.list()
                    logAuto("AWG: своих серверов ${servers.size}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 4) DPI candidate logging
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

# 5) кандидаты: пробуем/выбран
old = """        for (candidate in candidates) {
            if (!candidate()) continue
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {"""
new = """        for ((i, candidate) in candidates.withIndex()) {
            logAuto("пробуем кандидата #${i + 1}")
            if (!candidate()) continue
            logAuto("кандидат #${i + 1} выбран: ${controller.currentProfileId}")
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 6) строгая политика + завершение
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

old = """                lastAutoSwitchAt = System.currentTimeMillis()
                return true"""
new = """                lastAutoSwitchAt = System.currentTimeMillis()
                logAuto("переключение завершено: профиль=${controller.currentProfileId}")
                return true"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 7) Telegram DC logging
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
                    logAuto("Telegram DC через DPI недоступны (${dc.error ?: "нет ответа"}) -> ищем AWG/WARP")
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

# 8) публичные свежесть-лог (если есть наш блок)
old = """                        if (allowed.isEmpty()) logAuto("публичные: согласия нет или страна не свежая (>7 дней)")"""
if s.count(old) == 1:
    print('public freshness log already present')
open(p, 'w', encoding='utf-8').write(s)
print('AUTO log wired')
