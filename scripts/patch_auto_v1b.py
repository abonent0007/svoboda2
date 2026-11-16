import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

# 1) AUTO branch в connectManual и connectLastKnown
for fn in ('connectManual', 'connectLastKnown'):
    old = f"    suspend fun {fn}(): Boolean {{\n        if (prefs.connectionMethod == \"DPI_DIRECT\") {{"
    new = (f"    suspend fun {fn}(): Boolean {{\n"
           "        if (prefs.connectionMethod == \"AUTO\") {\n"
           "            return autoConnect()\n"
           "        }\n"
           "        if (prefs.connectionMethod == \"DPI_DIRECT\") {")
    assert s.count(old) == 1, f'anchor {fn}: {s.count(old)}'
    s = s.replace(old, new, 1)

# 2) autoConnect + lastAutoSwitchAt
old = "    /** Сохранённые сгенерированные WARP-профили (новые сверху). */"
new = '''    /** AUTO v1: подбор транспорта по приоритету стран и порядку транспортов.
     *  Переключение по foreground-приложению НЕ делаем (ломает активные сессии) —
     *  выбираем один транспорт и держим его (антидребезг: autoHoldMinutes). */
    suspend fun autoConnect(): Boolean {
        val now = System.currentTimeMillis()
        if (prefs.autoHoldMinutes > 0 && controller.isConnected &&
            now - lastAutoSwitchAt < prefs.autoHoldMinutes * 60_000L
        ) {
            return true
        }

        val transports = prefs.autoTransportOrder.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val countries = prefs.autoCountryPriority.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val strict = prefs.autoCountryStrict

        val candidates = mutableListOf<suspend () -> Boolean>()

        transports.forEach { transport ->
            when (transport) {
                "AWG" -> {
                    val servers = serverStore.list()
                    countries.forEach { cc ->
                        servers.filter { it.countryCode.trim().uppercase() == cc }.forEach { srv ->
                            candidates += {
                                controller.currentProfileId = srv.id
                                _profileLabel.value = "AUTO · ${srv.countryName.ifBlank { srv.countryCode }} (AWG)"
                                controller.connect(srv.resolvedConfig)
                            }
                        }
                    }
                    servers.forEach { srv ->
                        candidates += {
                            controller.currentProfileId = srv.id
                            _profileLabel.value = "AUTO · ${srv.countryName.ifBlank { srv.countryCode }} (AWG)"
                            controller.connect(srv.resolvedConfig)
                        }
                    }
                }
                "WARP" -> {
                    val profiles = store.list()
                    countries.forEach { cc ->
                        profiles.filter { it.observedCountry.trim().uppercase() == cc }.forEach { prof ->
                            candidates += { connectGeneratedProfile(prof) }
                        }
                    }
                    candidates += { connectWarpWithGeneration() }
                }
                "DPI" -> {
                    candidates += {
                        controller.currentProfileId = "dpi"
                        _profileLabel.value = "AUTO · DPI"
                        AppGraph.dpi.start()
                    }
                }
            }
        }

        if (strict && candidates.isEmpty()) {
            _profileLabel.value = "AUTO · нет профиля нужной страны"
            return false
        }

        for (candidate in candidates) {
            if (candidate()) {
                lastAutoSwitchAt = System.currentTimeMillis()
                return true
            }
        }
        return false
    }

    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L

    /** Сохранённые сгенерированные WARP-профили (новые сверху). */'''
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 3) измерение страны после подключения к сгенерированному профилю
old = """        val ok = controller.connect(profile.config)
        if (ok) rememberGood(profile.config)
        return ok
    }"""
new = """        val ok = controller.connect(profile.config)
        if (ok) {
            rememberGood(profile.config)
            // Фактическая страна выхода (для фильтра NL/FI/US) — измеряем, а не гадаем.
            val exit = NetProbe.fetchExit()
            if (exit != null && exit.country.isNotBlank()) {
                store.updateCountry(profile, exit.country.uppercase(), System.currentTimeMillis())
            }
        }
        return ok
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 4) import NetProbe
old_i = "import svoboda.runet.abonent0007.warp.SeedCatalog\n"
if 'import svoboda.runet.abonent0007.net.NetProbe' not in s:
    s = s.replace(old_i, old_i + "import svoboda.runet.abonent0007.net.NetProbe\n", 1)

open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: AUTO v1 patched')

# ---------- HomeScreen: AUTO активна ----------
p = root + r'\ui\home\HomeScreen.kt'
s = open(p, encoding='utf-8').read()
old = '    AUTO("AUTO", false),'
new = '    AUTO("AUTO", true),'
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('HomeScreen: AUTO enabled')
