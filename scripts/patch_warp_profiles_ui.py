p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()
old = "    /** Удалить сгенерированный профиль из хранилища. */"
new = """    /** Сохранённые сгенерированные WARP-профили (новые сверху). */
    fun generatedProfiles(): List<svoboda.runet.abonent0007.warp.GeneratedProfile> =
        store.list().sortedByDescending { it.createdAt }

    fun addGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile) {
        store.add(profile)
    }

    fun clearGeneratedProfiles() {
        store.clear()
    }

    /** Удалить сгенерированный профиль из хранилища. */"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: profile list API added')

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# imports
old = "import svoboda.runet.abonent0007.dpi.dpiStrategyById\n"
new = old + "import svoboda.runet.abonent0007.warp.SeedCatalog\nimport svoboda.runet.abonent0007.warp.WarpProfileFactory\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

anchor = "    MtuRow(mtu = mtu, onMtu = onMtu)\n}"
assert s.count(anchor) == 1, f'anchor count {s.count(anchor)}'
ui = """    MtuRow(mtu = mtu, onMtu = onMtu)

    SectionTitle("WARP-ПРОФИЛИ")
    var profileCount by remember { mutableStateOf(10) }
    var profileBusy by remember { mutableStateOf(false) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf(AppGraph.manager.generatedProfiles()) }

    Text(
        text = "Массовое создание конфигов: одна регистрация в Cloudflare, разные anycast-endpoint'ы " +
            "и рандомизированные junk-параметры (Jc/Jmin/Jmax) — профили не выглядят одинаково для DPI.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(5, 10, 25).forEach { n ->
            TextButton(onClick = { profileCount = n }) {
                Text(
                    text = "$n",
                    color = if (profileCount == n) AccentGreen else TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
    TileRow(
        title = "Создать профили WARP",
        value = if (profileBusy) "регистрация и сборка…" else "новая identity + $profileCount конфигов",
        opaque = true,
        onClick = {
            if (profileBusy) return@TileRow
            profileBusy = true
            profileStatus = null
            scope.launch {
                val seedRaw = SeedCatalog.defaultSeed(AppGraph.seeds)?.raw_config.orEmpty()
                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                )
                result.profiles.forEach { AppGraph.manager.addGeneratedProfile(it) }
                profiles = AppGraph.manager.generatedProfiles()
                profileBusy = false
                profileStatus = if (result.profiles.isEmpty()) {
                    "Не удалось создать профили. ${result.note}"
                } else {
                    result.note
                }
            }
        },
    )
    profileStatus?.let {
        TileRow(title = "Результат", value = it, opaque = true)
    }
    profiles.forEach { profile ->
        val jc = Regex("(?m)^Jc\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        val jmin = Regex("(?m)^Jmin\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        val jmax = Regex("(?m)^Jmax\\s*=\\s*(\\d+)").find(profile.config)?.groupValues?.get(1).orEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${profile.host}:${profile.port}" +
                    (if (jc.isNotBlank()) "   ·   Jc $jc, Jmin $jmin, Jmax $jmax" else ""),
                color = TextPrimary,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        profileStatus = "Подключение ${profile.host}:${profile.port} …"
                        val ok = AppGraph.manager.connectGeneratedProfile(profile)
                        profileStatus = if (ok) {
                            "Подключено: ${profile.host}:${profile.port}"
                        } else {
                            "Не удалось подключить ${profile.host}:${profile.port}"
                        }
                    }
                }) { Text("ПОДКЛЮЧИТЬ", fontSize = 12.sp, color = AccentGreen) }
                TextButton(onClick = {
                    AppGraph.manager.removeGeneratedProfile(profile)
                    profiles = AppGraph.manager.generatedProfiles()
                }) { Text("УДАЛИТЬ", fontSize = 12.sp) }
            }
        }
    }
    if (profiles.isNotEmpty()) {
        TextButton(onClick = {
            AppGraph.manager.clearGeneratedProfiles()
            profiles = AppGraph.manager.generatedProfiles()
        }) { Text("УДАЛИТЬ ВСЕ ПРОФИЛИ", fontSize = 12.sp) }
    }
}"""
s = s.replace(anchor, ui, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpSettings: profiles UI added')
