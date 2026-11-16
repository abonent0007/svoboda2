p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    /** Фильтр стратегий бенчмарка (пусто = весь каталог)."""
addition = """    /** Число попыток на домен в бенчмарке: 3 — скрининг, 10 — валидация, 20 — финал. */
    var dpiBenchmarkAttempts: Int
        get() = prefs.getInt("dpi_benchmark_attempts", 3).coerceIn(1, 20)
        set(value) = prefs.edit().putInt("dpi_benchmark_attempts", value.coerceIn(1, 20)).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: dpiBenchmarkAttempts')
