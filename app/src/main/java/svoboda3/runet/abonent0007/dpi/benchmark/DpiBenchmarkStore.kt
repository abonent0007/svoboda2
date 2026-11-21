package svoboda3.runet.abonent0007.dpi.benchmark

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Итог одной стратегии (в памяти, для рейтинга и UI). */
data class DpiStrategyResult(
    val strategyId: String,
    val strategyName: String,
    val strategyArgs: String,
    val connected: Boolean,
    val domains: List<DpiDomainTestResult>,
    val route: BenchRoute = BenchRoute.SOCKS,
) {
    val successfulDomains: Int get() = domains.count { it.networkSuccess }
    val failedDomains: Int get() = domains.size - successfulDomains

    /** Сеть доступна, но сервер ограничил HTTP (401/403/429) — не полный успех. */
    val limitedDomains: Int get() = domains.count { it.httpLimited }

    /** Полный успех: сеть доступна и получен чистый HTTP-ответ. */
    val cleanDomains: Int get() = domains.count { it.cleanSuccess }

    /** Успехи по категориям (напр. "Видео" → 2). */
    val byCategory: Map<String, Int>
        get() = domains.groupBy { it.domain.category }
            .mapValues { (_, list) -> list.count { it.networkSuccess } }
    val successRate: Double
        get() = if (domains.isEmpty()) 0.0 else domains.count { it.success }.toDouble() / domains.size
    val medianHttpMs: Long?
        get() = domains.mapNotNull { it.medianHttpMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }
    val medianTlsMs: Long?
        get() = domains.mapNotNull { it.medianTlsMs }.sorted().let {
            if (it.isEmpty()) null else it[it.size / 2]
        }
    val errorCount: Int
        get() = domains.sumOf { d -> d.attempts.count { it.errorType != null } }
    val timeoutCount: Int
        get() = domains.sumOf { d -> d.attempts.count { it.errorType == "timeout" || it.errorType == "refused" || it.errorType == "no_route" } }
}

/**
 * Локальный журнал бенчмарка (SQLite, без внешних зависимостей).
 *
 * Хранит и итог, и каждую попытку — по ТЗ §7.
 */
class DpiBenchmarkStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dpi_benchmark.db", null, VERSION) {

    companion object {
        private const val VERSION = 2
        const val STATUS_RUNNING = "running"
        const val STATUS_FINISHED = "finished"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_INTERRUPTED = "interrupted"
        const val STATUS_PAUSED = "paused"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE test_session(
                session_id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                network_type TEXT,
                mapdns_enabled INTEGER DEFAULT 0,
                selected_domains TEXT,
                total_strategies INTEGER DEFAULT 0,
                completed_strategies INTEGER DEFAULT 0,
                status TEXT NOT NULL,
                best_strategy_id TEXT,
                original_strategy_id TEXT,
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
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE strategy_test(
                test_id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                strategy_id TEXT NOT NULL,
                strategy_name TEXT,
                strategy_args TEXT,
                started_at INTEGER,
                finished_at INTEGER,
                connected INTEGER DEFAULT 0,
                successful_domains INTEGER DEFAULT 0,
                failed_domains INTEGER DEFAULT 0,
                success_rate REAL DEFAULT 0,
                median_http_ms INTEGER,
                median_tls_ms INTEGER,
                error_count INTEGER DEFAULT 0,
                route_type TEXT DEFAULT 'SOCKS',
                outcome_summary TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE domain_test(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                test_id INTEGER NOT NULL,
                domain_id TEXT,
                domain TEXT,
                category TEXT,
                attempts INTEGER DEFAULT 0,
                dns_success INTEGER DEFAULT 0,
                resolved_ip TEXT,
                tcp_success INTEGER DEFAULT 0,
                tcp_latency_ms INTEGER,
                tls_success INTEGER DEFAULT 0,
                tls_latency_ms INTEGER,
                http_status INTEGER,
                http_success INTEGER DEFAULT 0,
                http_latency_ms INTEGER,
                retry_count INTEGER DEFAULT 0,
                error_type TEXT,
                error_message TEXT,
                route_type TEXT DEFAULT 'SOCKS',
                outcome TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE attempt(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                domain_test_id INTEGER NOT NULL,
                attempt_no INTEGER,
                dns_ok INTEGER DEFAULT 0,
                resolved_ip TEXT,
                tcp_ok INTEGER DEFAULT 0,
                tcp_ms INTEGER,
                tls_ok INTEGER DEFAULT 0,
                tls_ms INTEGER,
                http_status INTEGER,
                http_ok INTEGER DEFAULT 0,
                http_ms INTEGER,
                error_type TEXT,
                error_message TEXT,
                route_type TEXT DEFAULT 'SOCKS',
                dns_mode TEXT DEFAULT 'SOCKS_REMOTE'
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_strategy_session ON strategy_test(session_id)")
        db.execSQL("CREATE INDEX idx_domain_test ON domain_test(test_id)")
        db.execSQL("CREATE INDEX idx_attempt_domain ON attempt(domain_test_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("attempt", "domain_test", "strategy_test", "test_session").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    fun createSession(
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
    }

    fun updateSessionProgress(sessionId: Long, completed: Int, status: String, bestStrategyId: String?) {
        val values = ContentValues().apply {
            put("completed_strategies", completed)
            put("status", status)
            if (bestStrategyId != null) put("best_strategy_id", bestStrategyId)
        }
        writableDatabase.update("test_session", values, "session_id=?", arrayOf(sessionId.toString()))
    }

    fun finishSession(sessionId: Long, status: String, bestStrategyId: String?) {
        val values = ContentValues().apply {
            put("finished_at", System.currentTimeMillis())
            put("status", status)
            if (bestStrategyId != null) put("best_strategy_id", bestStrategyId)
        }
        writableDatabase.update("test_session", values, "session_id=?", arrayOf(sessionId.toString()))
    }

    fun insertStrategyResult(sessionId: Long, result: DpiStrategyResult, startedAt: Long, finishedAt: Long): Long {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("strategy_id", result.strategyId)
            put("strategy_name", result.strategyName)
            put("strategy_args", result.strategyArgs)
            put("started_at", startedAt)
            put("finished_at", finishedAt)
            put("connected", if (result.connected) 1 else 0)
            put("successful_domains", result.successfulDomains)
            put("failed_domains", result.failedDomains)
            put("success_rate", result.successRate)
            put("median_http_ms", result.medianHttpMs)
            put("median_tls_ms", result.medianTlsMs)
            put("error_count", result.errorCount)
            put("route_type", result.route.name)
            put("outcome_summary", result.domains.groupingBy { it.outcome.name }.eachCount().toString())
        }
        val testId = writableDatabase.insertOrThrow("strategy_test", null, values)
        result.domains.forEach { domain ->
            val domainValues = ContentValues().apply {
                put("test_id", testId)
                put("domain_id", domain.domain.id)
                put("domain", domain.domain.domain)
                put("category", domain.domain.category)
                put("attempts", domain.attempts.size)
                put("dns_success", if (domain.attempts.any { it.dnsOk }) 1 else 0)
                put("resolved_ip", domain.attempts.firstNotNullOfOrNull { it.resolvedIp })
                put("tcp_success", if (domain.attempts.any { it.tcpOk }) 1 else 0)
                put("tcp_latency_ms", domain.attempts.mapNotNull { it.tcpMs }.minOrNull())
                put("tls_success", if (domain.attempts.any { it.tlsOk }) 1 else 0)
                put("tls_latency_ms", domain.medianTlsMs)
                put("http_status", domain.attempts.firstNotNullOfOrNull { it.httpStatus })
                put("http_success", if (domain.success) 1 else 0)
                put("http_latency_ms", domain.medianHttpMs)
                put("retry_count", domain.attempts.count { !it.transportOk(domain.domain) })
                put("error_type", domain.errorType)
                put("error_message", domain.errorMessage)
                put("route_type", domain.route.name)
                put("outcome", domain.outcome.name)
            }
            val domainId = writableDatabase.insertOrThrow("domain_test", null, domainValues)
            domain.attempts.forEach { attempt ->
                writableDatabase.insertOrThrow("attempt", null, ContentValues().apply {
                    put("domain_test_id", domainId)
                    put("attempt_no", attempt.attemptNo)
                    put("dns_ok", if (attempt.dnsOk) 1 else 0)
                    put("resolved_ip", attempt.resolvedIp)
                    put("tcp_ok", if (attempt.tcpOk) 1 else 0)
                    put("tcp_ms", attempt.tcpMs)
                    put("tls_ok", if (attempt.tlsOk) 1 else 0)
                    put("tls_ms", attempt.tlsMs)
                    put("http_status", attempt.httpStatus)
                    put("http_ok", if (attempt.httpOk) 1 else 0)
                    put("http_ms", attempt.httpMs)
                    put("error_type", attempt.errorType)
                    put("error_message", attempt.errorMessage)
                    put("route_type", attempt.route.name)
                    put("dns_mode", if (attempt.route == BenchRoute.SOCKS) "SOCKS_REMOTE" else "LOCAL")
                })
            }
        }
        return testId
    }

    /** Незавершённые сессии (для предложения продолжить). */
    fun interruptedSessions(): List<Long> {
        val ids = ArrayList<Long>()
        readableDatabase.query(
            "test_session",
            arrayOf("session_id"),
            "status=?",
            arrayOf(STATUS_INTERRUPTED),
            null, null, "started_at DESC",
        ).use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        return ids
    }

    fun sessionSnapshot(sessionId: Long): SessionSnapshot? {
        readableDatabase.query(
            "test_session",
            null,
            "session_id=?",
            arrayOf(sessionId.toString()),
            null, null, null,
        ).use { c ->
            if (!c.moveToFirst()) return null
            return SessionSnapshot(
                sessionId = c.getLong(c.getColumnIndexOrThrow("session_id")),
                startedAt = c.getLong(c.getColumnIndexOrThrow("started_at")),
                networkType = c.getString(c.getColumnIndexOrThrow("network_type")) ?: "",
                mapdnsEnabled = c.getInt(c.getColumnIndexOrThrow("mapdns_enabled")) == 1,
                selectedDomains = (c.getString(c.getColumnIndexOrThrow("selected_domains")) ?: "")
                    .split(',').filter { it.isNotBlank() },
                totalStrategies = c.getInt(c.getColumnIndexOrThrow("total_strategies")),
                completedStrategies = c.getInt(c.getColumnIndexOrThrow("completed_strategies")),
                status = c.getString(c.getColumnIndexOrThrow("status")) ?: "",
                bestStrategyId = c.getString(c.getColumnIndexOrThrow("best_strategy_id")),
                originalStrategyId = c.getString(c.getColumnIndexOrThrow("original_strategy_id")) ?: "",
                originalCustomArgs = c.getString(c.getColumnIndexOrThrow("original_custom_args")) ?: "",
                originalMapdns = c.getInt(c.getColumnIndexOrThrow("original_mapdns")) == 1,
            )
        }
    }

    data class SessionSnapshot(
        val sessionId: Long,
        val startedAt: Long,
        val networkType: String,
        val mapdnsEnabled: Boolean,
        val selectedDomains: List<String>,
        val totalStrategies: Int,
        val completedStrategies: Int,
        val status: String,
        val bestStrategyId: String?,
        val originalStrategyId: String,
        val originalCustomArgs: String,
        val originalMapdns: Boolean,
    )
}
