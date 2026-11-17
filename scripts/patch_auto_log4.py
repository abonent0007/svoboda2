import re

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

def sub_once(text, pattern, replacement, name):
    new, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    assert count == 1, f'{name}: {count}'
    return new

# 1) autoLog system
anchor = "\n    /** Время последнего автоматического переключения (антидребезг). */\n    private var lastAutoSwitchAt: Long = 0L\n"
addition = anchor + """
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
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)

# 2) anti-bounce + start log
s = sub_once(
    s,
    r"(\n        \) \{\n            return true\n        \}\n        val transports)",
    "\n        ) {\n            logAuto(\"anti-bounce: hold ${prefs.autoHoldMinutes}m\")\n            return true\n        }\n        logAuto(\"AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}\")\n        val transports",
    'anti-bounce',
)

# 3) AWG count
s = sub_once(
    s,
    r'("AWG" -> \{\n                    val servers = serverStore\.list\(\)\n)',
    '"AWG" -> {\n                    val servers = serverStore.list()\n                    logAuto("AWG: own servers=${servers.size}")\n',
    'awg',
)

# 4) DPI candidate log
s = sub_once(
    s,
    r'("DPI" -> \{\n                    candidates \+= \{\n                        controller\.currentProfileId = "dpi"\n)',
    '"DPI" -> {\n                    candidates += {\n                        logAuto("DPI candidate")\n                        controller.currentProfileId = "dpi"\n',
    'dpi',
)

# 5) candidate loop
s = sub_once(
    s,
    r'        for \(candidate in candidates\) \{\n            if \(!candidate\(\)\) continue\n            val isDpi',
    '        for ((i, candidate) in candidates.withIndex()) {\n            logAuto("try candidate #${i + 1}")\n            if (!candidate()) continue\n            logAuto("candidate #${i + 1} selected: ${controller.currentProfileId}")\n            val isDpi',
    'loop',
)

# 6) строгая политика
s = sub_once(
    s,
    r"(\n        if \(strict && candidates\.isEmpty\(\) \{\n            )",
    "\n        if (strict && candidates.isEmpty()) {\n            logAuto(\"strict: no candidates — abort\")\n            ",
    'strict',
)

# 7) переключение завершено
s = sub_once(
    s,
    r"(\n                lastAutoSwitchAt = System\.currentTimeMillis\(\)\n                return true)",
    "\n                lastAutoSwitchAt = System.currentTimeMillis()\n                logAuto(\"done: profile=${controller.currentProfileId}\")\n                return true",
    'done',
)

# 8) Telegram DC
s = sub_once(
    s,
    r"(if \(!dc\.reachable\) \{\n)",
    "if (!dc.reachable) {\n                    logAuto(\"Telegram DC over DPI down (${dc.error ?: \"no answer\"}) -> AWG/WARP\")\n",
    'dc-fail',
)
s = sub_once(
    s,
    r"(\} else \{\n                    autoStatus = \"Telegram DC)",
    "} else {\n                    logAuto(\"Telegram DC reachable\")\n                    autoStatus = \"Telegram DC",
    'dc-ok',
)

open(p, 'w', encoding='utf-8').write(s)
print('AUTO log wired')
