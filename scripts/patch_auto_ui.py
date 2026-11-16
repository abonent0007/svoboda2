import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'
p = root + r'\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# ---------- 1) AutoSettings: новая модель AUTO v1 ----------
m = re.search(r"@Composable\nprivate fun AutoSettings\(\) \{.*?\n\}\n", s, re.DOTALL)
assert m, 'AutoSettings not found'
new_auto = '''@Composable
private fun AutoSettings() {
    val prefs = AppGraph.prefs
    var transportOrder by remember { mutableStateOf(prefs.autoTransportOrder) }
    var countryPriority by remember { mutableStateOf(prefs.autoCountryPriority) }
    var strict by remember { mutableStateOf(prefs.autoCountryStrict) }
    var holdMinutes by remember { mutableStateOf(prefs.autoHoldMinutes.toString()) }

    SectionTitle("AUTO · АВТОМАТИЧЕСКИЙ ПОДБОР")
    Text(
        text = "AUTO выбирает один транспорт по правилам и держит его. Переключение по " +
            "foreground-приложению НЕ делается — это рвёт активные сессии (загрузка файла, " +
            "передача в Telegram). При падении туннеля — ограниченный переход на следующего кандидата.",
        color = TextSecondary,
        fontSize = 11.sp,
    )

    Text(
        text = "Порядок транспортов",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("AWG" to "AWG", "WARP" to "WARP", "DPI" to "DPI").forEach { (id, title) ->
            val active = transportOrder.split(',').any { it.trim().uppercase() == id }
            TextButton(onClick = {
                val current = transportOrder.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val next = if (active) current - id else current + id
                transportOrder = next.joinToString(",")
                prefs.autoTransportOrder = transportOrder
            }) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = if (active) AccentGreen else TextSecondary,
                )
            }
        }
    }

    Text(
        text = "Приоритет стран выхода",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("NL", "FI", "US").forEach { code ->
            val active = countryPriority.split(',').any { it.trim().uppercase() == code }
            TextButton(onClick = {
                val current = countryPriority.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val next = if (active) current - code else current + code
                countryPriority = next.joinToString(",")
                prefs.autoCountryPriority = countryPriority
            }) {
                Text(
                    text = code,
                    fontSize = 12.sp,
                    color = if (active) AccentGreen else TextSecondary,
                )
            }
        }
    }

    TileRow(
        title = "Строгая страна",
        value = if (strict) "не уходить в другие страны" else "разрешены другие страны",
        opaque = true,
        control = {
            NeonSwitch(strict) {
                strict = it
                prefs.autoCountryStrict = it
            }
        },
    )
    OutlinedTextField(
        value = holdMinutes,
        onValueChange = {
            holdMinutes = it.filter { ch -> ch.isDigit() }.take(3)
            holdMinutes.toIntOrNull()?.let { prefs.autoHoldMinutes = it }
        },
        label = { Text("Удерживать транспорт, мин (антидребезг)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Страна WARP = ближайший PoP Cloudflare и не гарантируется: у профилей измерение " +
            "фактической страны (после подключения), у своих AWG-серверов страна задаётся вами. " +
            "Для строгой гарантии NL/FI/US нужны собственные серверы в этих странах.",
        color = TextSecondary,
        fontSize = 11.sp,
    )
}
'''
s = s[:m.start()] + new_auto + s[m.end():]

# ---------- 2) пикер сервисов в WARP-разделе ----------
old = '    SectionTitle("DNS В КОНФИГЕ")'
new = '    ServiceScopePicker(title = "СЕРВИСЫ В ТУННЕЛЬ (WARP)")\n\n' + old
assert s.count(old) == 1
s = s.replace(old, new, 1)

# ---------- 3) пикер сервисов в DPI-разделе ----------
old = '    SectionTitle("СТРАТЕГИИ")'
new = '    ServiceScopePicker(title = "СЕРВИСЫ В ТУННЕЛЬ (DPI)")\n\n' + old
assert s.count(old) == 1
s = s.replace(old, new, 1)

# ---------- 4) страна выхода в списке WARP-профилей ----------
old = """                text = "${profile.host}:${profile.port}" +
                    (if (jc.isNotBlank()) "   ·   Jc $jc, Jmin $jmin, Jmax $jmax" else ""),"""
new = """                text = "${profile.host}:${profile.port}" +
                    (if (jc.isNotBlank()) "   ·   Jc $jc, Jmin $jmin, Jmax $jmax" else "") +
                    (if (profile.observedCountry.isNotBlank()) "   ·   ${profile.observedCountry}" else ""),"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('MethodSettings: AUTO UI + service scope + country labels')
