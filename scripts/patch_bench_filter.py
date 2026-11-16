root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# 1) Prefs: предыдущая стратегия + фильтр стратегий бенчмарка
p = root + r'\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    /** DNS через туннель (HEV mapdns): домены резолвятся внутри обхода, соединения идут по домену. */"""
addition = """    /** Предыдущая пользовательская DPI-стратегия (для отката). */
    var dpiCustomStrategyPrev: String
        get() = prefs.getString("dpi_custom_strategy_prev", "") ?: ""
        set(value) = prefs.edit().putString("dpi_custom_strategy_prev", value).apply()

    /** Фильтр стратегий бенчмарка (пусто = весь каталог). Нужен для повторной проверки победителей. */
    var dpiBenchmarkStrategyIds: Set<String>
        get() = prefs.getStringSet("dpi_benchmark_strategy_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("dpi_benchmark_strategy_ids", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: prev strategy + benchmark filter')

# 2) Manager: фильтр стратегий в прогоне
p = root + r'\dpi\benchmark\DpiBenchmarkManager.kt'
s = open(p, encoding='utf-8').read()
old = """            for ((index, strategy) in DPI_CATALOG.withIndex()) {"""
new = """            val filter = prefs.dpiBenchmarkStrategyIds
            val catalog = if (filter.isEmpty()) DPI_CATALOG else DPI_CATALOG.filter { filter.contains(it.id) }
            for ((index, strategy) in catalog.withIndex()) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                        strategyIndex = index + 1,
                        totalStrategies = DPI_CATALOG.size,"""
new = """                        strategyIndex = index + 1,
                        totalStrategies = catalog.size,"""
assert s.count(old) == 2
s = s.replace(old, new)

old = """            totalStrategies = DPI_CATALOG.size,"""
new = """            totalStrategies = if (prefs.dpiBenchmarkStrategyIds.isEmpty()) DPI_CATALOG.size else prefs.dpiBenchmarkStrategyIds.size,"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Manager: strategy filter')
