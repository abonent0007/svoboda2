import re

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\dpi\benchmark\DpiBenchmarkStore.kt'
s = open(p, encoding='utf-8').read()

# версия схемы → 2 (старый тестовый журнал можно пересоздать)
s = s.replace("private const val VERSION = 1", "private const val VERSION = 2", 1)

# --- test_session: метаданные окружения ---
old = """                original_strategy_id TEXT,
                original_custom_args TEXT,
                original_mapdns INTEGER DEFAULT 0
            )"""
new = """                original_strategy_id TEXT,
                original_custom_args TEXT,
                original_mapdns INTEGER DEFAULT 0,
                campaign_seed INTEGER,
                strategy_order TEXT,
                app_version TEXT,
                android_api INTEGER,
                device_model TEXT,
                carrier TEXT,
                mtu INTEGER,
                dns_preset TEXT,
                exit_country TEXT
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- strategy_test: route_type ---
old = """                median_tls_ms INTEGER,
                error_count INTEGER DEFAULT 0
            )"""
new = """                median_tls_ms INTEGER,
                error_count INTEGER DEFAULT 0,
                route_type TEXT DEFAULT 'SOCKS',
                outcome_summary TEXT
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- domain_test: route_type + outcome ---
old = """                retry_count INTEGER DEFAULT 0,
                error_type TEXT,
                error_message TEXT
            )"""
new = """                retry_count INTEGER DEFAULT 0,
                error_type TEXT,
                error_message TEXT,
                route_type TEXT DEFAULT 'SOCKS',
                outcome TEXT
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- attempt: route_type ---
old = """                http_ms INTEGER,
                error_type TEXT,
                error_message TEXT
            )"""
new = """                http_ms INTEGER,
                error_type TEXT,
                error_message TEXT,
                route_type TEXT DEFAULT 'SOCKS',
                dns_mode TEXT DEFAULT 'SOCKS_REMOTE'
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- createSession: новые поля ---
old = """    fun createSession(
        startedAt: Long,
        networkType: String,
        mapdnsEnabled: Boolean,
        selectedDomains: List<String>,
        totalStrategies: Int,
        originalStrategyId: String,
        originalCustomArgs: String,
        originalMapdns: Boolean,
    ): Long {
        val values = ContentValues().apply {
            put("started_at", startedAt)
            put("network_type", networkType)
            put("mapdns_enabled", if (mapdnsEnabled) 1 else 0)
            put("selected_domains", selectedDomains.joinToString(","))
            put("total_strategies", totalStrategies)
            put("completed_strategies", 0)
            put("status", STATUS_RUNNING)
            put("original_strategy_id", originalStrategyId)
            put("original_custom_args", originalCustomArgs)
            put("original_mapdns", if (originalMapdns) 1 else 0)
        }
        return writableDatabase.insertOrThrow("test_session", null, values)
    }"""
new = """    fun createSession(
        startedAt: Long,
        networkType: String,
        mapdnsEnabled: Boolean,
        selectedDomains: List<String>,
        totalStrategies: Int,
        originalStrategyId: String,
        originalCustomArgs: String,
        originalMapdns: Boolean,
        campaignSeed: Long = 0L,
        strategyOrder: List<String> = emptyList(),
        appVersion: String = "",
        androidApi: Int = 0,
        deviceModel: String = "",
        carrier: String = "",
        mtu: Int = 0,
        dnsPreset: String = "",
    ): Long {
        val values = ContentValues().apply {
            put("started_at", startedAt)
            put("network_type", networkType)
            put("mapdns_enabled", if (mapdnsEnabled) 1 else 0)
            put("selected_domains", selectedDomains.joinToString(","))
            put("total_strategies", totalStrategies)
            put("completed_strategies", 0)
            put("status", STATUS_RUNNING)
            put("original_strategy_id", originalStrategyId)
            put("original_custom_args", originalCustomArgs)
            put("original_mapdns", if (originalMapdns) 1 else 0)
            put("campaign_seed", campaignSeed)
            put("strategy_order", strategyOrder.joinToString(","))
            put("app_version", appVersion)
            put("android_api", androidApi)
            put("device_model", deviceModel)
            put("carrier", carrier)
            put("mtu", mtu)
            put("dns_preset", dnsPreset)
        }
        return writableDatabase.insertOrThrow("test_session", null, values)
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- insertStrategyResult: route_type + outcome ---
old = """            put("median_http_ms", result.medianHttpMs)
            put("median_tls_ms", result.medianTlsMs)
            put("error_count", result.errorCount)
        }"""
new = """            put("median_http_ms", result.medianHttpMs)
            put("median_tls_ms", result.medianTlsMs)
            put("error_count", result.errorCount)
            put("route_type", result.route.name)
            put("outcome_summary", result.domains.groupingBy { it.outcome.name }.eachCount().toString())
        }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                put("retry_count", domain.attempts.count { !it.networkOk(domain.domain) })
                put("error_type", domain.errorType)
                put("error_message", domain.errorMessage)
            }"""
new = """                put("retry_count", domain.attempts.count { !it.transportOk(domain.domain) })
                put("error_type", domain.errorType)
                put("error_message", domain.errorMessage)
                put("route_type", domain.route.name)
                put("outcome", domain.outcome.name)
            }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                    put("http_ms", attempt.httpMs)
                    put("error_type", attempt.errorType)
                    put("error_message", attempt.errorMessage)
                })"""
new = """                    put("http_ms", attempt.httpMs)
                    put("error_type", attempt.errorType)
                    put("error_message", attempt.errorMessage)
                    put("route_type", attempt.route.name)
                    put("dns_mode", if (attempt.route == BenchRoute.SOCKS) "SOCKS_REMOTE" else "LOCAL")
                })"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# --- DpiStrategyResult: route ---
old = """data class DpiStrategyResult(
    val strategyId: String,
    val strategyName: String,
    val strategyArgs: String,
    val connected: Boolean,
    val domains: List<DpiDomainTestResult>,
) {"""
new = """data class DpiStrategyResult(
    val strategyId: String,
    val strategyName: String,
    val strategyArgs: String,
    val connected: Boolean,
    val domains: List<DpiDomainTestResult>,
    val route: BenchRoute = BenchRoute.SOCKS,
) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('store: schema v2 (route_type, metadata, outcomes)')
