package svoboda.runet.abonent0007.dpi.benchmark

/**
 * Рейтинг стратегий (по ревью GPT к ТЗ).
 *
 * Ключевая идея: без baseline (DIRECT) абсолютное число «успешных доменов» ничего
 * не значит — нужно знать, что именно обход открыл (gain) и что сломал (регрессии).
 *
 * Порядок сравнения:
 *  1. Эффективность обхода — макро-оценка по категориям (нормализация, чтобы
 *     «веб» с 20 доменами не подавлял «Telegram» с 3);
 *  2. Покрытие категорий;
 *  3. Регрессии (то, что работало напрямую и перестало);
 *  4. Стабильность (доля успешных попыток);
 *  5. Задержка — медиана и P90 HTTP.
 */
/** Домен → результат DIRECT-замера (baseline). */
typealias BenchBaseline = Map<String, DpiDomainTestResult>

object DpiStrategyRanker {

    /** Сколько доменов обход ОТКРЫЛ (в DIRECT не работало, через стратегию работает). */
    fun bypassGain(result: DpiStrategyResult, baseline: BenchBaseline): Int =
        result.domains.count { d ->
            val direct = baseline[d.domain.id]
            direct != null && !direct.networkSuccess && d.networkSuccess
        }

    /** Сколько доменов обход СЛОМАЛ (в DIRECT работало, через стратегию нет). */
    fun regressions(result: DpiStrategyResult, baseline: BenchBaseline): Int =
        result.domains.count { d ->
            val direct = baseline[d.domain.id]
            direct != null && direct.networkSuccess && !d.networkSuccess
        }

    /** Макро-оценка: средняя по категориям доля доступных доменов (0..1). */
    fun macroScore(result: DpiStrategyResult): Double {
        val byCategory = result.domains.groupBy { it.domain.category }
        if (byCategory.isEmpty()) return 0.0
        return byCategory.values.sumOf { list ->
            list.count { it.networkSuccess }.toDouble() / list.size
        } / byCategory.size
    }

    /** Доля категорий, где стратегия покрывает хотя бы половину доменов. */
    fun categoryCoverage(result: DpiStrategyResult): Double {
        val byCategory = result.domains.groupBy { it.domain.category }
        if (byCategory.isEmpty()) return 0.0
        return byCategory.count { (_, list) ->
            list.count { it.networkSuccess } * 2 >= list.size
        }.toDouble() / byCategory.size
    }

    fun p90HttpMs(result: DpiStrategyResult): Long? {
        val values = result.domains.mapNotNull { it.p90HttpMs }.sorted()
        if (values.isEmpty()) return null
        return values[values.size / 2]
    }

    /** Основной рейтинг. */
    fun rank(results: List<DpiStrategyResult>, baseline: BenchBaseline): List<DpiStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiStrategyResult> { macroScore(it) }
                .thenByDescending { categoryCoverage(it) }
                .thenByDescending { bypassGain(it, baseline) }
                .thenBy { regressions(it, baseline) }
                .thenByDescending { it.successRate }
                .thenBy { it.medianHttpMs ?: Long.MAX_VALUE }
                .thenBy { p90HttpMs(it) ?: Long.MAX_VALUE }
        )

    /** Лучший универсальный профиль. */
    fun best(results: List<DpiStrategyResult>, baseline: BenchBaseline): DpiStrategyResult? =
        rank(results, baseline).firstOrNull()

    /** Лучший профиль по категории. */
    fun bestByCategory(
        results: List<DpiStrategyResult>,
        baseline: BenchBaseline,
        category: String,
    ): DpiStrategyResult? = results
        .filter { r -> r.domains.any { it.domain.category == category } }
        .sortedWith(
            compareByDescending<DpiStrategyResult> { r ->
                val list = r.domains.filter { it.domain.category == category }
                if (list.isEmpty()) 0.0 else list.count { it.networkSuccess }.toDouble() / list.size
            }
                .thenByDescending { r ->
                    r.domains.filter { it.domain.category == category }.count { it.cleanSuccess }
                }
                .thenBy { r ->
                    r.domains.filter { it.domain.category == category }
                        .mapNotNull { it.medianHttpMs }.sorted()
                        .let { list -> if (list.isEmpty()) Long.MAX_VALUE else list[list.size / 2] }
                }
        )
        .firstOrNull()

    /** Самые быстрые среди стратегий, прошедших порог успешности. */
    fun lowLatency(
        results: List<DpiStrategyResult>,
        minSuccessRate: Double = 0.6,
    ): List<DpiStrategyResult> = results
        .filter { it.successRate >= minSuccessRate }
        .sortedWith(
            compareBy<DpiStrategyResult> { it.medianHttpMs ?: Long.MAX_VALUE }
                .thenBy { p90HttpMs(it) ?: Long.MAX_VALUE }
        )

    /** Все категории, по которым есть данные. */
    fun categories(results: List<DpiStrategyResult>): List<String> =
        results.flatMap { r -> r.domains.map { it.domain.category } }.distinct().sorted()
}
