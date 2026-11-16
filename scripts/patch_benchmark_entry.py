p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# 1) import
old = "import svoboda.runet.abonent0007.dpi.dpiStrategyById\n"
new = old + "import svoboda.runet.abonent0007.ui.benchmark.DpiBenchmarkDialog\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) state in MethodSettingsDialog
old = """fun MethodSettingsDialog(
    method: ConnectionMethod,
    mtu: Int,
    onMtu: (Int) -> Unit,
    onOpenServers: () -> Unit,
    onDismiss: () -> Unit,
) {"""
new = """fun MethodSettingsDialog(
    method: ConnectionMethod,
    mtu: Int,
    onMtu: (Int) -> Unit,
    onOpenServers: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showBenchmark by remember { mutableStateOf(false) }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 3) pass callback to DpiSettings
old = "                    ConnectionMethod.DPI_DIRECT -> DpiSettings()"
new = "                    ConnectionMethod.DPI_DIRECT -> DpiSettings(onOpenBenchmark = { showBenchmark = true })"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 4) DpiSettings signature + button in ДВИЖОК card
old = "@Composable\nprivate fun DpiSettings() {"
new = "@Composable\nprivate fun DpiSettings(onOpenBenchmark: () -> Unit = {}) {"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        DpiComment("Домены резолвятся внутри обхода (198.18.0.2), соединения идут по домену через ciadpi.\\nНе зависит от DNS провайдера и подмены. Не обходит IP-блокировку Telegram.\\nЭкспериментально: применится при следующем подключении.")
    }"""
new = """        DpiComment("Домены резолвятся внутри обхода (198.18.0.2), соединения идут по домену через ciadpi.\\nНе зависит от DNS провайдера и подмены. Не обходит IP-блокировку Telegram.\\nЭкспериментально: применится при следующем подключении.")
        TileRow(
            title = "Тестирование стратегий",
            value = "бенчмарк: сравнить стратегии по доменам",
            opaque = true,
            onClick = onOpenBenchmark,
        )
        DpiComment("Бенчмарк по очереди перезапускает ciadpi с каждой стратегией каталога, проверяет выбранные домены (DNS/TCP/TLS/HTTP) и строит рейтинг. Исходные настройки стратегии восстанавливаются после прогона.")
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 5) render benchmark dialog inside the settings dialog
old = """                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ЗАКРЫТЬ", color = TextSecondary)
                }"""
new = """                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("ЗАКРЫТЬ", color = TextSecondary)
                }

                if (showBenchmark) {
                    DpiBenchmarkDialog(onDismiss = { showBenchmark = false })
                }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('benchmark entry point added')
