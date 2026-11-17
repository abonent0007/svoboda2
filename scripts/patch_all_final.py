import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# ---------- 1) ConnectionManager: вставить autoLog system после lastAutoSwitchAt ----------
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

if 'fun logAuto' not in s:
    m = re.search(r'(\n    /\*\* Время последнего автоматического переключения[^\n]*\n    private var lastAutoSwitchAt: Long = 0L\n)', s)
    assert m, 'lastAutoSwitchAt anchor'
    addition = m.group(1) + """    /** Журнал решений AUTO (одной строкой на событие), хранится локально. */
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
    s = s[:m.start()] + addition + s[m.end():]
    print('autoLog system inserted')

# anti-bounce + start
m = re.search(r'(\n        \) \{\n            return true\n        \}\n\n        val transports)', s)
if m and 'anti-bounce' not in s:
    replacement = """\n        ) {
            logAuto("anti-bounce: hold ${'$'}{prefs.autoHoldMinutes}m")
            return true
        }
        logAuto("AUTO start; order=${'$'}{prefs.autoTransportOrder}; countries=${'$'}{prefs.autoCountryPriority}")

        val transports"""
    s = s[:m.start()] + replacement + s[m.end():]
    print('anti-bounce logging')

# AWG count
m = re.search(r'("AWG" -> \{\n                    val servers = serverStore\.list\(\)\n)', s)
if m and 'AWG: own servers' not in s:
    s = s[:m.start()] + '"AWG" -> {\n                    val servers = serverStore.list()\n                    logAuto("AWG: own servers=${servers.size}")\n' + s[m.end():]
    print('awg log')

# DPI candidate
m = re.search(r'("DPI" -> \{\n                    candidates \+= \{\n                        controller\.currentProfileId = "dpi"\n)', s)
if m and 'DPI candidate' not in s:
    s = s[:m.start()] + '"DPI" -> {\n                    candidates += {\n                        logAuto("DPI candidate")\n                        controller.currentProfileId = "dpi"\n' + s[m.end():]
    print('dpi log')

# candidate loop (replace outer loop header)
m = re.search(r'        for \(candidate in candidates\) \{\n            if \(!candidate\(\)\) continue\n            val isDpi', s)
if m and 'try candidate' not in s:
    replacement = """        for ((i, candidate) in candidates.withIndex()) {
            logAuto("try candidate #${'$'}{i + 1}")
            if (!candidate()) continue
            logAuto("candidate #${'$'}{i + 1} selected: ${'$'}{controller.currentProfileId}")
            val isDpi"""
    s = s[:m.start()] + replacement + s[m.end():]
    print('candidate loop log')

# строгая политика — regex with two closing parens
m = re.search(r'\n\n        if \(strict && candidates\.isEmpty\(\)\) \{\n            ', s)
if m and 'strict: no candidates' not in s:
    s = s[:m.start()] + '\n\n        if (strict && candidates.isEmpty()) {\n            logAuto("strict: no candidates — abort")\n            ' + s[m.end():]
    print('strict log')

# переключение завершено — flexible whitespace
m = re.search(r'\n\s*lastAutoSwitchAt = System\.currentTimeMillis\(\)\n\s*return true', s)
if m and 'done: profile=' not in s:
    s = s[:m.start()] + '\n            lastAutoSwitchAt = System.currentTimeMillis()\n            logAuto("done: profile=${controller.currentProfileId}")\n            return true' + s[m.end():]
    print('done log')

# Telegram DC logs
m = re.search(r'if \(!dc\.reachable\) \{\n', s)
if m and 'Telegram DC over DPI down' not in s:
    s = s[:m.start()] + 'if (!dc.reachable) {\n                    logAuto("Telegram DC over DPI down (${dc.error ?: \"no answer\"}) -> AWG/WARP")\n' + s[m.end():]
    print('dc down log')

m = re.search(r'\} else \{\n                    autoStatus = "Telegram DC', s)
if m and 'Telegram DC reachable' not in s:
    s = s[:m.start()] + '} else {\n                    logAuto("Telegram DC reachable")\n                    autoStatus = "Telegram DC' + s[m.end():]
    print('dc reachable log')

open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager ok')

# ---------- 2) PublicServerCatalog: заменить meta-блок целиком ----------
p = root + r'\own\PublicServerCatalog.kt'
s = open(p, encoding='utf-8').read()
pattern = re.compile(r'    private val meta = listOf\(\n.*?    \)\n', re.DOTALL)
m = pattern.search(s)
assert m, 'meta block anchor'
new_block = """    // IP вписаны на дату 2026-11-17 (публичные хосты nl2/nl3 — мёртвые, NXDOMAIN;
    // nl.* жив, fi.* жив; engage.cloudflareclient.com — официальный endpoint WARP).
    private val meta = listOf(
        PublicServer("nl_1", "Нидерланды · 1010", "NL", "31.76.110.142", 1010, "public_servers/nl_1.conf"),
        PublicServer("nl_2", "Нидерланды · 878", "NL", "31.76.110.142", 878, "public_servers/nl_2.conf"),
        PublicServer("nl_3", "Нидерланды · 891", "NL", "31.76.110.142", 891, "public_servers/nl_3.conf"),
        PublicServer("fi_1", "Финляндия · 1843", "FI", "144.31.28.185", 1843, "public_servers/fi_1.conf"),
        PublicServer("fi_2", "Финляндия · 864", "FI", "144.31.28.185", 864, "public_servers/fi_2.conf"),
        PublicServer("fi_3", "Финляндия · 934", "FI", "144.31.28.185", 934, "public_servers/fi_3.conf"),
        PublicServer("any_1", "Anycast · страна после измерения · 3138", "US", "engage.cloudflareclient.com", 3138, "public_servers/any_1.conf"),
        PublicServer("any_2", "Anycast · страна после измерения · 7156", "US", "engage.cloudflareclient.com", 7156, "public_servers/any_2.conf"),
        PublicServer("any_3", "Anycast · страна после измерения · 903", "US", "engage.cloudflareclient.com", 903, "public_servers/any_3.conf"),
    )
"""
s = s[:m.start()] + new_block + s[m.end():]
open(p, 'w', encoding='utf-8').write(s)
print('PublicServerCatalog ok')
