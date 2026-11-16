p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

# 1) AUTO: если выбран DPI, но DC Telegram недоступны — пробуем AWG/WARP
old = """        for (candidate in candidates) {
            if (candidate()) {
                lastAutoSwitchAt = System.currentTimeMillis()
                return true
            }
        }
        return false
    }"""
new = """        var dpiFallback = false
        for (candidate in candidates) {
            if (!candidate()) continue
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {
                // Telegram-приложение ходит к IP-адресам DC: DPI-стратегии их не открывают.
                // Если DC недоступны — ищем транспорт AWG/WARP, и только потом откатываемся на DPI.
                val dc = TelegramDc.check()
                if (!dc.reachable) {
                    autoStatus = "Telegram DC недоступны через DPI (${dc.error ?: "нет ответа"}) — ищем AWG/WARP"
                    if (!dpiFallback) {
                        dpiFallback = true
                        continue
                    }
                } else {
                    autoStatus = "Telegram DC доступны"
                }
            }
            lastAutoSwitchAt = System.currentTimeMillis()
            return true
        }
        if (dpiFallback) {
            autoStatus = "Telegram DC недоступны: подключён DPI (AWG/WARP не найден)"
        }
        return false
    }

    /** Статус AUTO для UI (последняя причина выбора/отказа). */
    @Volatile
    var autoStatus: String? = null"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old_i = "import svoboda.runet.abonent0007.net.NetProbe\n"
new_i = old_i + "import svoboda.runet.abonent0007.net.TelegramDc\n"
assert s.count(old_i) == 1
s = s.replace(old_i, new_i, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: Telegram DC logic')

# 2) Prefs: флаг "Telegram через VPN"
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    /** AUTO: минимальное время удержания выбранного транспорта, мин (антидребезг). */"""
addition = """    /** AUTO: если DC Telegram недоступны через DPI — выбирать AWG/WARP. */
    var autoTelegramViaVpn: Boolean
        get() = prefs.getBoolean("auto_telegram_via_vpn", true)
        set(value) = prefs.edit().putBoolean("auto_telegram_via_vpn", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: autoTelegramViaVpn')
