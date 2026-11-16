p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# imports
old = "import androidx.compose.foundation.verticalScroll\n"
new = "import androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.verticalScroll\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = "import svoboda.runet.abonent0007.warp.SeedCatalog\n"
new = old + "import svoboda.runet.abonent0007.warp.WarpGenerator\nimport svoboda.runet.abonent0007.split.ServiceCatalog\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# вставляем секции DNS и сервисов перед "WARP-ПРОФИЛИ"
anchor = '    SectionTitle("WARP-ПРОФИЛИ")'
assert s.count(anchor) == 1
sections = """    SectionTitle("DNS В КОНФИГЕ")
    var dnsPreset by remember { mutableStateOf("cloudflare") }
    Text(
        text = "Какой резолвер писать в сгенерированные конфиги. Антиблок-резолверы (malw.link, " +
            "bezmezhau.com, geohide.ru, geodema.love) разрешаются в IP при создании профиля, " +
            "так как AmneziaWG принимает только адреса.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        WarpGenerator.DNS_PRESETS.forEach { preset ->
            TextButton(onClick = { dnsPreset = preset.id }) {
                Text(
                    text = preset.title,
                    fontSize = 12.sp,
                    color = if (dnsPreset == preset.id) AccentGreen else TextSecondary,
                )
            }
        }
    }

    SectionTitle("СЕРВИСЫ НАПРЯМУЮ")
    var directServices by remember { mutableStateOf(prefs.serviceDirectSelection) }
    Text(
        text = "Раздельное туннелирование: выбранные приложения идут мимо туннеля (мимо WARP). " +
            "Пакеты определяются по названию установленного приложения. LAN-трафик при этом " +
            "и так идёт напрямую — физические подсети приоритетнее маршрута 0/0 туннеля.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    ServiceCatalog.services.chunked(2).forEach { rowServices ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rowServices.forEach { service ->
                val selected = directServices.contains(service.id)
                TextButton(onClick = {
                    directServices = if (selected) directServices - service.id else directServices + service.id
                    prefs.serviceDirectSelection = directServices
                }) {
                    Text(
                        text = service.title,
                        fontSize = 12.sp,
                        color = if (selected) AccentGreen else TextSecondary,
                    )
                }
            }
        }
    }

""" + anchor
s = s.replace(anchor, sections, 1)

# генерация профилей использует выбранный DNS
old = """                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                )"""
new = """                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                    dns = WarpGenerator.resolveDns(dnsPreset),
                )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('WarpSettings: DNS presets + service-direct UI added')
