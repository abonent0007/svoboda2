p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\benchmark\DpiStrategyRanker.kt'
s = open(p, encoding='utf-8').read()

old = """object DpiStrategyRanker {

    fun rank(results: List<DpiStrategyResult>): List<DpiStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiStrategyResult> { it.successfulDomains }
                .thenByDescending { it.successRate }
                .thenBy { it.medianHttpMs ?: Long.MAX_VALUE }
                .thenBy { it.timeoutCount }
                .thenBy { it.medianTlsMs ?: Long.MAX_VALUE }
        )

    fun best(results: List<DpiStrategyResult>): DpiStrategyResult? = rank(results).firstOrNull()
}"""
new = """object DpiStrategyRanker {

    /** Базовый порядок стратегий (ТЗ §6). */
    fun rank(results: List<DpiStrategyResult>): List<DpiStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiStrategyResult> { it.successfulDomains }
                .thenByDescending { it.successRate }
                .thenBy { it.medianHttpMs ?: Long.MAX_VALUE }
                .thenBy { it.timeoutCount }
                .thenBy { it.medianTlsMs ?: Long.MAX_VALUE }
        )

    /** Лучший универсальный профиль — максимум доступных доменов. */
    fun best(results: List<DpiStrategyResult>): DpiStrategyResult? = rank(results).firstOrNull()

    /**
     * Лучший профиль по категории (YouTube / AI / обычный веб и т.д.).
     * Стратегия, которая хороша для Cloudflare, может не подходить для YouTube —
     * поэтому считаем отдельно по каждой категории.
     */
    fun bestByCategory(
        results: List<DpiStrategyResult>,
        category: String,
    ): DpiStrategyResult? = results
        .filter { r -> r.domains.any { it.domain.category == category } }
        .sortedWith(
            compareByDescending<DpiStrategyResult> { r -> r.byCategory[category] ?: 0 }
                .thenByDescending { r -> r.domains.filter { it.domain.category == category }.count { it.cleanSuccess } }
                .thenBy { r ->
                    r.domains.filter { it.domain.category == category }
                        .mapNotNull { it.medianHttpMs }
                        .sorted()
                        .let { list -> if (list.isEmpty()) Long.MAX_VALUE else list[list.size / 2] }
                }
                .thenBy { r -> r.domains.count { it.domain.category == category && !it.networkSuccess } }
        )
        .firstOrNull()

    /** Все категории, по которым есть данные. */
    fun categories(results: List<DpiStrategyResult>): List<String> =
        results.flatMap { r -> r.domains.map { it.domain.category } }.distinct().sorted()
}"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ranker: per-category best')
