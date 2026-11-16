p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\benchmark\DpiBenchmarkDialog.kt'
s = open(p, encoding='utf-8').read()

# 1. imports
old = "import androidx.compose.ui.window.DialogProperties\n"
new = "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.window.DialogProperties\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = "import svoboda.runet.abonent0007.vpn.VpnPhase\n"
new = "import svoboda.runet.abonent0007.vpn.VpnPhase\nimport kotlinx.coroutines.launch\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2. export: pass context from composable
old = """                        onExport = { exportNote = exportResults(current) },"""
new = """                        onExport = { exportNote = exportResults(LocalContext.current, current) },"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """private fun exportResults(state: DpiBenchmarkState.Finished): String {
    val context = AppGraph.dpiBenchmarkStore.javaClass // placeholder, не используется
    val app = AppGraph.connectionState // не используется
    return try {
        val dir = java.io.File(AppGraph.prefs.javaClass.let { _ -> android.os.Environment.getExternalStorageDirectory() }, "x")
        // Экспорт пишем в приватный каталог приложения через store-контекст недоступен — используем кэш.
        val out = java.io.File(androidx.compose.ui.platform.LocalContext.current.filesDir, "exports")"""
new = """private fun exportResults(context: android.content.Context, state: DpiBenchmarkState.Finished): String {
    return try {
        val out = java.io.File(context.filesDir, "exports")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('benchmark UI fixed')
