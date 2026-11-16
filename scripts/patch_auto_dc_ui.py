p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

anchor = """    Text(
        text = "Страна WARP = ближайший PoP Cloudflare и не гарантируется:"""
addition = """    var dcStatus by remember { mutableStateOf(AppGraph.manager.autoStatus) }
    TileRow(
        title = "Telegram DC (MTProto)",
        value = dcStatus ?: "не проверялось",
        opaque = true,
        control = {
            NeonSwitch(prefs.autoTelegramViaVpn) {
                prefs.autoTelegramViaVpn = it
            }
        },
    )
    TextButton(onClick = {
        scope.launch {
            val status = svoboda.runet.abonent0007.net.TelegramDc.check()
            dcStatus = if (status.reachable) {
                "доступны (${status.reachableIps.firstOrNull()})"
            } else {
                "недоступны (${status.error ?: "нет ответа"}) — нужен AWG/WARP"
            }
        }
    }) { Text("ПРОВЕРИТЬ DC", fontSize = 12.sp) }
    Text(
        text = "Страна WARP = ближайший PoP Cloudflare и не гарантируется:"""
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AUTO UI: DC check row')
