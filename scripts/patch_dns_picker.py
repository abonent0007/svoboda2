import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# ---------- HomeScreen: DNS-диалог с пикером ----------
p = root + r'\ui\home\HomeScreen.kt'
s = open(p, encoding='utf-8').read()

old = """            text = {
                OutlinedTextField(
                    value = dnsText,
                    onValueChange = { dnsText = it },
                    singleLine = true,
                    placeholder = { Text("1.1.1.1, 8.8.8.8") },
                )
            },"""
new = """            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DnsResolverPanel(
                        current = dnsText,
                        onApply = { dnsText = it },
                    )
                }
            },"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# imports
old_i = "import svoboda.runet.abonent0007.ui.method.MethodSettingsDialog\n"
new_i = old_i + "import svoboda.runet.abonent0007.ui.method.DnsResolverPanel\n"
assert s.count(old_i) == 1
s = s.replace(old_i, new_i, 1)

for imp, anchor in (
    ("import androidx.compose.foundation.layout.heightIn\n", "import androidx.compose.foundation.layout.height\n"),
    ("import androidx.compose.foundation.rememberScrollState\n", "import androidx.compose.foundation.background\n"),
    ("import androidx.compose.foundation.verticalScroll\n", "import androidx.compose.foundation.background\n"),
):
    if imp not in s:
        assert s.count(anchor) == 1, anchor
        s = s.replace(anchor, imp + anchor, 1)

open(p, 'w', encoding='utf-8').write(s)
print('HomeScreen: DNS picker wired')

# ---------- MethodSettings: WARP DNS через пикер ----------
p = root + r'\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

old = """    SectionTitle("DNS В КОНФИГЕ")
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
    }"""
new = """    SectionTitle("DNS В КОНФИГЕ")
    var dnsValue by remember { mutableStateOf(WarpGenerator.resolveDns("cloudflare")) }
    Text(
        text = "Какой резолвер писать в сгенерированные конфиги. Доменные антиблок-резолверы " +
            "разрешаются в IP при создании профиля — AmneziaWG принимает только адреса.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    DnsResolverPanel(
        current = dnsValue,
        onApply = { dnsValue = it },
    )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                    dns = WarpGenerator.resolveDns(dnsPreset),
                )"""
new = """                val result = WarpProfileFactory.generate(
                    count = profileCount,
                    identity = null,
                    seedRawConfig = seedRaw,
                    dns = dnsValue,
                )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('MethodSettings: WARP DNS picker wired')
