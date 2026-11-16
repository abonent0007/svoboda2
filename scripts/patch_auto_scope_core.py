import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# ---------- 1) GeneratedProfile: измеренная страна выхода ----------
p = root + r'\warp\WarpModels.kt'
s = open(p, encoding='utf-8').read()
old = """data class GeneratedProfile(
    val createdAt: Long = 0L,
    val host: String = "",
    val port: Int = 0,
    val config: String = "",
    val configEncrypted: String = "",
)"""
new = """data class GeneratedProfile(
    val createdAt: Long = 0L,
    val host: String = "",
    val port: Int = 0,
    val config: String = "",
    val configEncrypted: String = "",
    /** Фактически измеренная страна выхода (ISO-код), напр. "NL". Пусто — не измерено. */
    val observedCountry: String = "",
    val countryCheckedAt: Long = 0L,
)"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpModels: observedCountry')

# ---------- 2) Prefs: пресеты AUTO и область сервисов ----------
p = root + r'\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = "    /** Сервисы, которые направляются мимо туннеля (раздельное туннелирование, WARP). */"
addition = """    /** Сервисы, чей трафик заворачивается в туннель (раздельное туннелирование по маршрутам). */
    var tunnelServiceScope: Set<String>
        get() = prefs.getStringSet("tunnel_service_scope", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("tunnel_service_scope", value).apply()

    /** AUTO: приоритет стран выхода (ISO-коды через запятую). */
    var autoCountryPriority: String
        get() = prefs.getString("auto_country_priority", "NL,FI,US") ?: "NL,FI,US"
        set(value) = prefs.edit().putString("auto_country_priority", value).apply()

    /** AUTO: порядок транспортов (AWG,WARP,DPI). */
    var autoTransportOrder: String
        get() = prefs.getString("auto_transport_order", "AWG,WARP,DPI") ?: "AWG,WARP,DPI"
        set(value) = prefs.edit().putString("auto_transport_order", value).apply()

    /** AUTO: строгая политика страны (не уходить в другие страны) или предпочтительная. */
    var autoCountryStrict: Boolean
        get() = prefs.getBoolean("auto_country_strict", false)
        set(value) = prefs.edit().putBoolean("auto_country_strict", value).apply()

    /** AUTO: минимальное время удержания выбранного транспорта, мин (антидребезг). */
    var autoHoldMinutes: Int
        get() = prefs.getInt("auto_hold_minutes", 5).coerceIn(0, 120)
        set(value) = prefs.edit().putInt("auto_hold_minutes", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: AUTO + scope')

# ---------- 3) DpiEngine: scope в синтетический конфиг ----------
p = root + r'\dpi\DpiEngine.kt'
s = open(p, encoding='utf-8').read()
old = """        val split = splitManager.resolve(vpn.currentProfileId.ifBlank { "warp" })
        val config = DpiConfig.build(prefs, split)"""
new = """        val split = splitManager.resolve(vpn.currentProfileId.ifBlank { "warp" })
        val scope = prefs.tunnelServiceScope
        val allowedIps = ServiceIpRanges.allowedIps(context, scope)
        val config = DpiConfig.build(prefs, split, scope, allowedIps)"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
if 'import svoboda.runet.abonent0007.warp.ServiceIpRanges' not in s:
    old_i = "import svoboda.runet.abonent0007.net.NetProbe\n"
    s = s.replace(old_i, old_i + "import svoboda.runet.abonent0007.warp.ServiceIpRanges\n", 1)
open(p, 'w', encoding='utf-8').write(s)
print('DpiEngine: scope wired')
