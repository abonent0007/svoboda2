package svoboda.runet.abonent0007.dpi.benchmark

/** Рейтинг стратегий по правилам ТЗ §6 (без сводного балла). */
object DpiStrategyRanker {

    fun rank(results: List<DpiStrategyResult>): List<DpiStrategyResult> =
        results.sortedWith(
            compareByDescending<DpiStrategyResult> { it.successfulDomains }
                .thenByDescending { it.successRate }
                .thenBy { it.medianHttpMs ?: Long.MAX_VALUE }
                .thenBy { it.timeoutCount }
                .thenBy { it.medianTlsMs ?: Long.MAX_VALUE }
        )

    fun best(results: List<DpiStrategyResult>): DpiStrategyResult? = rank(results).firstOrNull()
}
