import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# ---------- Prefs: убрать дубль autoPublicAllowed ----------
p = root + r'\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
old = """    /** Разрешить публичные (сторонние) серверы в AUTO (по умолчанию выключено). */
    var autoPublicAllowed: Boolean
        get() = prefs.getBoolean("auto_public_allowed", false)
        set(value) = prefs.edit().putBoolean("auto_public_allowed", value).apply()

"""
if old in s:
    s = s.replace(old, "", 1)
    print('duplicate pref removed')
else:
    print('duplicate pref not found (already ok)')

# Prefs: publicServerCountryAgeDays — оставить (если уже есть — не дублировать)
if s.count('fun publicServerCountryAgeDays') == 0:
    anchor = """        set(value) = prefs.edit().putStringSet("public_server_countries", value.map { "${it.key}=${it.value}" }.toSet()).apply()"""
    addition = anchor + """

    /** Возраст последнего измерения страны публичного сервера в днях (-1 если нет). */
    fun publicServerCountryAgeDays(id: String): Long {
        val raw = prefs.getStringSet("public_server_countries", emptySet())
            ?.firstOrNull { it.startsWith("$id=") }
            ?.substringAfter('=', "")
        ?: return -1
        val parts = raw.split('@', limit = 2)
        val ts = parts.getOrNull(1)?.toLongOrNull() ?: return Long.MAX_VALUE
        return (System.currentTimeMillis() - ts) / (24L * 60L * 60L * 1000L)
    }"""
    assert s.count(anchor) == 1
    s = s.replace(anchor, addition, 1)
    print('country age helper added')
else:
    print('age helper already present')
open(p, 'w', encoding='utf-8').write(s)

# ConnectionManager: использовать autoIncludePublicServers, добавить свежесть
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

old = """                    val public = if (prefs.autoIncludePublicServers) PublicServerCatalog.servers else emptyList()
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
new = """                    if (prefs.autoIncludePublicServers) {
                        val publicServers = PublicServerCatalog.servers
                        val measured = prefs.publicServerCountries
                        val priorityIndex: (String) -> Int = { cc ->
                            countries.indexOf(cc.trim().uppercase()).let { index -> if (index < 0) 99 else index }
                        }
                        val allowed = publicServers.filter { pub ->
                            val age = prefs.publicServerCountryAgeDays(pub.id)
                            val cc = (measured[pub.id] ?: "").substringBefore('@').uppercase()
                            val fresh = age in 0..7
                            if (fresh && countries.contains(cc)) true else !strict
                        }
                        allowed.sortedBy { pub ->
                            priorityIndex((measured[pub.id] ?: "").substringBefore('@').uppercase())
                        }.forEach { pub ->
                            candidates += { connectPublicServer(pub.id) }
                        }
                        if (allowed.isEmpty()) logAuto("публичные: согласия нет или страна не свежая (>7 дней)")
                    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# измерение страны сохраняется со свежей меткой времени
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
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager ok')
