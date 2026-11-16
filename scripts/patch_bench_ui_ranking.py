p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\benchmark\DpiBenchmarkDialog.kt'
s = open(p, encoding='utf-8').read()

# 1) run view: 403 wording
old = """                "HTTP: ${status(attempt.httpOk)}${attempt.httpStatus?.let { " · $it" } ?: ""}" +"""
new = """                "HTTP: ${status(attempt.httpOk)}${attempt.httpStatus?.let { " · $it" } ?: ""}" +
                (if (attempt.httpLimitedByServer()) " (сеть доступна, HTTP ограничен сервером)" else "") +"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) results: универсальный профиль + разбивка по качеству
old = """                Text(
                    text = "Доступно доменов: ${best.successfulDomains} / ${best.domains.size}   ·   " +
                        "Успех попыток: ${(best.successRate * 100).toInt()}%   ·   " +
                        "Медиана HTTP: ${best.medianHttpMs?.let { "$it мс" } ?: "—"}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )"""
new = """                Text(
                    text = "Доступно доменов: ${best.successfulDomains} / ${best.domains.size}   ·   " +
                        "Успех попыток: ${(best.successRate * 100).toInt()}%   ·   " +
                        "Медиана HTTP: ${best.medianHttpMs?.let { "$it мс" } ?: "—"}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = "Полных ответов (2xx/3xx): ${best.cleanDomains}   ·   " +
                        "Сеть есть, HTTP ограничен сервером (401/403/429): ${best.limitedDomains}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 3) results: лучшие по категориям
old = """    Text("РЕЙТИНГ", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)"""
new = """    val categories = DpiStrategyRanker.categories(ranked)
    if (categories.isNotEmpty()) {
        Text(
            text = "ЛУЧШИЕ ПО КАТЕГОРИЯМ",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        categories.forEach { category ->
            val categoryBest = DpiStrategyRanker.bestByCategory(ranked, category)
            if (categoryBest != null) {
                val inCategory = categoryBest.domains.filter { it.domain.category == category }
                val available = inCategory.count { it.networkSuccess }
                Text(
                    text = "$category: ${categoryBest.strategyId} · ${categoryBest.strategyName}   —   " +
                        "сеть $available/${inCategory.size}, полных ${inCategory.count { it.cleanSuccess }}",
                    color = TextPrimary,
                    fontSize = 11.sp,
                )
            }
        }
    }

    Text("РЕЙТИНГ", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 4) ranking rows: показать ограниченные
old = """                Text(
                    text = "успешно ${result.successfulDomains}/${result.domains.size}   ·   " +
                        "${(result.successRate * 100).toInt()}%   ·   " +
                        "медиана ${result.medianHttpMs?.let { "$it мс" } ?: "—"}   ·   " +
                        "ошибок ${result.errorCount}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )"""
new = """                Text(
                    text = "сеть ${result.successfulDomains}/${result.domains.size}   ·   " +
                        "полных ${result.cleanDomains}   ·   " +
                        "ограничено ${result.limitedDomains}   ·   " +
                        "${(result.successRate * 100).toInt()}%   ·   " +
                        "медиана ${result.medianHttpMs?.let { "$it мс" } ?: "—"}   ·   " +
                        "ошибок ${result.errorCount}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('benchmark UI: category cards + 403 wording')
