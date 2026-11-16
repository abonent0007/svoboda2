# 1) DpiEngine: restartWithCurrentStrategy (по текущим prefs: custom важнее каталога)
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\DpiEngine.kt'
s = open(p, encoding='utf-8').read()
anchor = "    /** Готовность локального SOCKS5 (порт принимает соединения). */"
addition = """    /** Перезапуск ciadpi с текущей конфигурацией (пользовательская строка приоритетнее каталога). */
    suspend fun restartWithCurrentStrategy(): Boolean {
        stopCiadpi()
        Thread.sleep(400)
        return startCiadpi()
    }

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('DpiEngine: restartWithCurrentStrategy added')

# 2) ReconnectCoordinator: не вмешиваться во время бенчмарка
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ReconnectCoordinator.kt'
s = open(p, encoding='utf-8').read()
old = """    private fun checkTunnel(): Boolean {
        return runBlocking {"""
new = """    private fun checkTunnel(): Boolean {
        // Во время бенчмарка стратегий health-проверка не должна вмешиваться:
        // ciadpi перезапускается, и контрольный SOCKS-проб был бы ложным сигналом.
        if (testingMode) return true
        return runBlocking {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ReconnectCoordinator: testingMode respected')

# 3) Prefs: сохранение выбранных доменов бенчмарка
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    var dpiMapDns: Boolean
        get() = prefs.getBoolean("dpi_map_dns", false)
        set(value) = prefs.edit().putBoolean("dpi_map_dns", value).apply()
"""
addition = anchor + """
    /** Выбранные домены для бенчмарка DPI (сохраняются между запусками). */
    var dpiBenchmarkDomains: Set<String>
        get() = prefs.getStringSet("dpi_benchmark_domains", null)
            ?: svoboda.runet.abonent0007.dpi.benchmark.DpiDomainCatalog.defaultSelection
        set(value) = prefs.edit().putStringSet("dpi_benchmark_domains", value).apply()
"""
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: dpiBenchmarkDomains added')

# 4) AppGraph: store + manager
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\AppGraph.kt'
s = open(p, encoding='utf-8').read()
anchor = "    lateinit var dpi: svoboda.runet.abonent0007.dpi.DpiEngine\n"
addition = anchor + "    lateinit var dpiBenchmarkStore: svoboda.runet.abonent0007.dpi.benchmark.DpiBenchmarkStore\n    lateinit var dpiBenchmark: svoboda.runet.abonent0007.dpi.benchmark.DpiBenchmarkManager\n"
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)

old = "        AppGraph.vpn.onDpiDisconnect = { AppGraph.dpi.stopProcess() }\n"
new = old + """
        AppGraph.dpiBenchmarkStore = svoboda.runet.abonent0007.dpi.benchmark.DpiBenchmarkStore(this)
        AppGraph.dpiBenchmark = svoboda.runet.abonent0007.dpi.benchmark.DpiBenchmarkManager(
            context = this,
            prefs = AppGraph.prefs,
            engine = AppGraph.dpi,
            store = AppGraph.dpiBenchmarkStore,
            scope = AppGraph.appScope,
            onTestingModeChanged = { active -> AppGraph.reconnect.testingMode = active },
        )
"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AppGraph: benchmark wired')
