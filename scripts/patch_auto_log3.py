import re

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

# 2) anti-bounce + start log
old = """        ) {
            return true
        }
        val transports"""
new = """        ) {
            logAuto("anti-bounce: hold ${prefs.autoHoldMinutes}m")
            return true
        }
        logAuto("AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}")
        val transports"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 3) AWG count
old = """                "AWG" -> {
                    val servers = serverStore.list()"""
new = """                "AWG" -> {
                    val servers = serverStore.list()
                    logAuto("AWG: own servers=${servers.size}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 4) DPI candidate log
old = """                "DPI" -> {
                    candidates += {
                        controller.currentProfileId = "dpi\""""
new = """                "DPI" -> {
                    candidates += {
                        logAuto("DPI candidate")
                        controller.currentProfileId = "dpi\""""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 5) candidate loop
old = """        for (candidate in candidates) {
            if (!candidate()) continue
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {"""
new = """        for ((i, candidate) in candidates.withIndex()) {
            logAuto("try candidate #${i + 1}")
            if (!candidate()) continue
            logAuto("candidate #${i + 1} selected: ${controller.currentProfileId}")
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 6) строгая политика — regex-safe anchor
pattern = re.compile(r"(\n        if \(strict && candidates\.isEmpty\(\) \{\n            )")
m = pattern.search(s)
assert m, 'strict anchor'
s = s[:m.start()] + '\n        if (strict && candidates.isEmpty()) {\n            logAuto("strict: no candidates — abort")\n            ' + s[m.end():]

# 7) завершение переключения
old = """                lastAutoSwitchAt = System.currentTimeMillis()
                return true"""
new = """                lastAutoSwitchAt = System.currentTimeMillis()
                logAuto("done: profile=${controller.currentProfileId}")
                return true"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 8) Telegram DC log
pattern = re.compile(r"(if \(!dc\.reachable\) \{\n)")
m = pattern.search(s)
if m:
    s = s[:m.start()] + 'if (!dc.reachable) {\n                    logAuto("Telegram DC over DPI unreachable (${dc.error ?: "no answer"}) -> AWG/WARP")\n                    ' + s[m.end():]

pattern = re.compile(r"(\} else \{\n\s+autoStatus = \"Telegram DC доступны\"\n)")
m = pattern.search(s)
if m:
    s = s[:m.start()] + '} else {\n                    logAuto("Telegram DC reachable")\n                    autoStatus = "Telegram DC доступны"\n                }' + s[m.end():]

open(p, 'w', encoding='utf-8').write(s)
print('AUTO log wired')
