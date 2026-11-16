p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\benchmark\DpiBenchmarkDialog.kt'
s = open(p, encoding='utf-8').read()

# 1) переименование метода в новом тестере
s = s.replace("httpLimitedByServer()", "serverLimited()")

# 2) baseline в ResultsView
old = """private fun ResultsView(
    state: DpiBenchmarkState.Finished,
    onApply: (String) -> Unit,
    onExport: () -> Unit,
) {
    val ranked = DpiStrategyRanker.rank(state.results)"""
new = """private fun ResultsView(
    state: DpiBenchmarkState.Finished,
    baseline: Map<String, DpiDomainTestResult>,
    onApply: (String) -> Unit,
    onExport: () -> Unit,
) {
    val ranked = DpiStrategyRanker.rank(state.results, baseline)"""
assert s.count(old) == 1, 'ResultsView anchor'
s = s.replace(old, new, 1)

old = """                    is DpiBenchmarkState.Finished -> ResultsView(
                        state = current,"""
new = """                    is DpiBenchmarkState.Finished -> ResultsView(
                        state = current,
                        baseline = current.baseline,"""
assert s.count(old) == 1, 'call anchor'
s = s.replace(old, new, 1)

# 3) baseline-блок после строки с номером сессии
old = """        text = "Протестировано стратегий: ${ranked.size}   ·   сессия #${state.sessionId}",
        color = TextSecondary,
        fontSize = 11.sp,
    )"""
new = """        text = "Протестировано стратегий: ${ranked.size}   ·   сессия #${state.sessionId}",
        color = TextSecondary,
        fontSize = 11.sp,
    )
    if (baseline.isNotEmpty()) {
        val directOk = baseline.values.count { it.networkSuccess }
        val directFull = baseline.values.count { it.cleanSuccess }
        Text(
            text = "DIRECT (без обхода): сеть доступна на $directOk/${baseline.size}, полных ответов $directFull",
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }"""
assert s.count(old) == 1, 'session line anchor'
s = s.replace(old, new, 1)

# 4) gain/регрессии/макро в карточке лучшего — вставляем перед строкой с аргументами
old = """                Text(text = best.strategyArgs, color = TextSecondary, fontSize = 10.sp)"""
new = """                Text(
                    text = "Обход открыл: ${DpiStrategyRanker.bypassGain(best, baseline)}   ·   " +
                        "регрессий: ${DpiStrategyRanker.regressions(best, baseline)}   ·   " +
                        "макро-оценка: ${(DpiStrategyRanker.macroScore(best) * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
                Text(text = best.strategyArgs, color = TextSecondary, fontSize = 10.sp)"""
assert s.count(old) == 1, 'best args anchor'
s = s.replace(old, new, 1)

# 5) bestByCategory с baseline
old = "DpiStrategyRanker.bestByCategory(ranked, category)"
new = "DpiStrategyRanker.bestByCategory(ranked, baseline, category)"
assert s.count(old) == 1, 'category anchor'
s = s.replace(old, new, 1)

# 6) в строках рейтинга добавляем gain/регрессии
old = """                        "${(result.successRate * 100).toInt()}%   ·   " +"""
new = """                        "открыл ${DpiStrategyRanker.bypassGain(result, baseline)}   ·   " +
                        "регрессий ${DpiStrategyRanker.regressions(result, baseline)}   ·   " +
                        "${(result.successRate * 100).toInt()}%   ·   " +"""
assert s.count(old) == 1, 'ranking row anchor'
s = s.replace(old, new, 1)

# 7) выбор числа попыток
old = """    if (!dpiConnected || !methodIsDpi) {"""
new = """    var attempts by remember { mutableStateOf(AppGraph.prefs.dpiBenchmarkAttempts) }
    Text(
        text = "Попыток на домен",
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(3 to "3 · скрининг", 10 to "10 · валидация", 20 to "20 · финал").forEach { (n, title) ->
            val active = attempts == n
            TextButton(onClick = {
                attempts = n
                AppGraph.prefs.dpiBenchmarkAttempts = n
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
        text = "Сначала идёт скрининг всех стратегий, затем (вручную) повтор на 5 лучших с 10 попытками " +
            "и финал на 2 лучших с 20 попытками — так отсеивается случайный выигрыш.",
        color = TextSecondary,
        fontSize = 10.sp,
    )

    if (!dpiConnected || !methodIsDpi) {"""
assert s.count(old) == 1, 'attempts anchor'
s = s.replace(old, new, 1)

# 8) импорт DpiDomainTestResult
old_i = "import svoboda.runet.abonent0007.dpi.benchmark.DpiStrategyRanker\n"
new_i = old_i + "import svoboda.runet.abonent0007.dpi.benchmark.DpiDomainTestResult\n"
assert s.count(old_i) == 1
s = s.replace(old_i, new_i, 1)

open(p, 'w', encoding='utf-8').write(s)
print('benchmark UI v2 applied')
