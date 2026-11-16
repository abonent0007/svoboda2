p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\benchmark\DpiBenchmarkStore.kt'
s = open(p, encoding='utf-8').read()

# 1) stats in-memory
old = """    val successfulDomains: Int get() = domains.count { it.success }
    val failedDomains: Int get() = domains.size - successfulDomains"""
new = """    val successfulDomains: Int get() = domains.count { it.networkSuccess }
    val failedDomains: Int get() = domains.size - successfulDomains

    /** Сеть доступна, но сервер ограничил HTTP (401/403/429) — не полный успех. */
    val limitedDomains: Int get() = domains.count { it.httpLimited }

    /** Полный успех: сеть доступна и получен чистый HTTP-ответ. */
    val cleanDomains: Int get() = domains.count { it.cleanSuccess }

    /** Успехи по категориям (напр. "Видео" → 2). */
    val byCategory: Map<String, Int>
        get() = domains.groupBy { it.domain.category }
            .mapValues { (_, list) -> list.count { it.networkSuccess } }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) колонку не добавляем (схема v1 уже на устройстве): limited считается из attempt-ов
open(p, 'w', encoding='utf-8').write(s)
print('store: stats without schema change')
