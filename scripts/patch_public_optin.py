p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    /** AUTO: если DC Telegram недоступны через DPI — выбирать AWG/WARP. */"""
addition = """    /** AUTO: использовать встроенные публичные серверы (чужие операторы). По умолчанию выключено. */
    var autoIncludePublicServers: Boolean
        get() = prefs.getBoolean("auto_include_public_servers", false)
        set(value) = prefs.edit().putBoolean("auto_include_public_servers", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: autoIncludePublicServers')

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()
old = """                    val public = PublicServerCatalog.servers"""
new = """                    val public = if (prefs.autoIncludePublicServers) PublicServerCatalog.servers else emptyList()"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AUTO: public servers only with opt-in')

# UI: opt-in переключатель в AUTO
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()
old = """    var strict by remember { mutableStateOf(prefs.autoCountryStrict) }"""
new = old + "\n    var usePublic by remember { mutableStateOf(prefs.autoIncludePublicServers) }"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """    OutlinedTextField(
        value = holdMinutes,"""
new = """    TileRow(
        title = "Публичные серверы в AUTO",
        value = if (usePublic) "учтены (неизвестные операторы)" else "не учитывать",
        opaque = true,
        control = {
            NeonSwitch(usePublic) {
                usePublic = it
                prefs.autoIncludePublicServers = it
            }
        },
    )
    Text(
        text = "ВНИМАНИЕ: публичные серверы — коллективные identity чужих операторов: оно видит " +
            "метаданные ваших соединений (объём/время/IP назначений). Без строгой необходимости " +
            "лучше свой AWG-сервер в NL/FI/US.",
        color = TextSecondary,
        fontSize = 10.sp,
    )
    OutlinedTextField(
        value = holdMinutes,"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# предупреждение в секции публичных серверов (AWG)
old = """        text = "Публично опубликованные WARP-конфиги (Delta-Kronecker/Cloudflare-Warp), " +"""
new = """        text = "ВНИМАНИЕ: чужие операторы (коллективные identity), метаданные соединений им видны. " +
            "Строго NL/FI/US без своего сервера не гарантируются. Рекомендуется свой VPS. " +
            "Публично опубликованные WARP-конфиги (Delta-Kronecker/Cloudflare-Warp), " +"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('UI: public opt-in + warning')
