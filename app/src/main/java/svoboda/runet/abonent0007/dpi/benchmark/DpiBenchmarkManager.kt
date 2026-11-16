package svoboda.runet.abonent0007.dpi.benchmark

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.dpi.DPI_CATALOG
import svoboda.runet.abonent0007.dpi.DpiEngine
import svoboda.runet.abonent0007.dpi.DpiStrategy
import java.net.InetAddress
import kotlin.random.Random

/** Состояние экрана/сессии бенчмарка. */
sealed interface DpiBenchmarkState {
    data object Idle : DpiBenchmarkState

    data class Preparing(val message: String) : DpiBenchmarkState

    data class Running(
        val strategyIndex: Int,
        val totalStrategies: Int,
        val strategyId: String,
        val strategyName: String,
        val domainsChecked: Int,
        val totalDomains: Int,
        val successful: Int,
        val failed: Int,
        val currentDomain: String?,
        val currentAttempt: DpiAttemptResult?,
        val elapsedMs: Long,
        val route: BenchRoute = BenchRoute.SOCKS,
        val baselineDone: Boolean = false,
    ) : DpiBenchmarkState

    data class Paused(val running: Running, val reason: String) : DpiBenchmarkState

    data class Finished(
        val sessionId: Long,
        val results: List<DpiStrategyResult>,
        val baseline: Map<String, DpiDomainTestResult>,
        val best: DpiStrategyResult?,
        val appliedStrategyId: String? = null,
    ) : DpiBenchmarkState

    data class Failed(val reason: String) : DpiBenchmarkState
}

/**
 * Менеджер сессии бенчмарка.
 *
 * Методология (по ревью GPT):
 *  - один campaignSeed на сессию: порядок стратегий перемешан, и ВСЕ стратегии
 *    внутри раунда идут по ОДНОМУ И ТОМУ ЖЕ порядку доменов → сравнение честное;
 *  - DIRECT-baseline (без обхода) меряется до раунда, повторно каждые ~6 стратегий
 *    и в конце — видно, что даёт обход (gain) и что он ломает (регрессии);
 *  - число попыток на домен настраивается (3 — скрининг, 10 — валидация, 20 — финал).
 */
class DpiBenchmarkManager(
    private val context: Context,
    private val prefs: Prefs,
    private val engine: DpiEngine,
    private val store: DpiBenchmarkStore,
    private val scope: CoroutineScope,
    private val onTestingModeChanged: (Boolean) -> Unit = {},
) {

    private val _state = MutableStateFlow<DpiBenchmarkState>(DpiBenchmarkState.Idle)
    val state: StateFlow<DpiBenchmarkState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var sessionId: Long = 0L

    private var originalStrategyId: String = ""
    private var originalCustomArgs: String = ""
    private var originalMapDns: Boolean = false

    fun start(domainIds: Set<String>) {
        if (job?.isActive == true) return
        val domains = domainIds.mapNotNull { DpiDomainCatalog.byId(it) }
        if (domains.isEmpty()) {
            _state.value = DpiBenchmarkState.Failed("Не выбрано ни одного домена")
            return
        }
        stopped = false
        paused = false
        job = scope.launch(Dispatchers.IO) { runSession(domains) }
    }

    fun pause(reason: String = "Пауза") {
        val current = _state.value
        if (current is DpiBenchmarkState.Running) {
            paused = true
            _state.value = DpiBenchmarkState.Paused(current, reason)
        }
    }

    fun resume() {
        paused = false
        val current = _state.value
        if (current is DpiBenchmarkState.Paused) _state.value = current.running
    }

    fun stop() {
        stopped = true
        paused = false
    }

    /** Применить выбранную стратегию: сохранить как пользовательскую и перезапустить ciadpi. */
    suspend fun applyStrategy(strategyId: String): Boolean = withContext(Dispatchers.IO) {
        val strategy = DPI_CATALOG.firstOrNull { it.id == strategyId } ?: return@withContext false
        prefs.dpiStrategyId = strategyId
        prefs.dpiCustomStrategy = strategy.args
        val ok = engine.restartWithCurrentStrategy()
        if (ok) {
            val current = _state.value
            if (current is DpiBenchmarkState.Finished) {
                _state.value = current.copy(appliedStrategyId = strategyId)
            }
        }
        ok
    }

    fun reset() {
        _state.value = DpiBenchmarkState.Idle
    }

    // ------------------------------------------------------------------

    private suspend fun runSession(domains: List<DpiTestDomain>) {
        originalStrategyId = prefs.dpiStrategyId
        originalCustomArgs = prefs.dpiCustomStrategy
        originalMapDns = prefs.dpiMapDns

        val campaignSeed = System.nanoTime()
        val filter = prefs.dpiBenchmarkStrategyIds
        val catalog = (if (filter.isEmpty()) DPI_CATALOG else DPI_CATALOG.filter { filter.contains(it.id) })
            .shuffled(Random(campaignSeed))
        val domainOrder = domains.shuffled(Random(campaignSeed xor 0x5DEECE66DL))

        val attempts = prefs.dpiBenchmarkAttempts.coerceIn(1, 20)
        val tester = DpiDomainTester(socksPort = prefs.dpiSocksPort.coerceIn(1, 65535), attemptsPerDomain = attempts)

        sessionId = store.createSession(
            startedAt = System.currentTimeMillis(),
            networkType = currentNetworkType(),
            mapdnsEnabled = prefs.dpiMapDns,
            selectedDomains = domainOrder.map { it.id },
            totalStrategies = catalog.size,
            originalStrategyId = originalStrategyId,
            originalCustomArgs = originalCustomArgs,
            originalMapdns = originalMapDns,
            campaignSeed = campaignSeed,
            strategyOrder = catalog.map { it.id },
            appVersion = appVersion(),
            androidApi = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            carrier = carrierName(),
            mtu = prefs.tunnelMtu,
            dnsPreset = prefs.dnsServers.ifBlank { "1.1.1.1" },
        )
        onTestingModeChanged(true)

        val results = ArrayList<DpiStrategyResult>(catalog.size)
        var baseline = measureBaseline(tester, domainOrder)
        val sessionStart = System.currentTimeMillis()

        try {
            for ((index, strategy) in catalog.withIndex()) {
                if (stopped) break
                while (paused && !stopped) delay(300)
                if (stopped) break

                _state.value = DpiBenchmarkState.Preparing("Запуск стратегии ${strategy.id} …")

                if (!controlProbe()) {
                    _state.value = DpiBenchmarkState.Failed(
                        "Нет сети вне туннеля (ciadpi не сможет выйти в интернет). Тестирование остановлено."
                    )
                    store.finishSession(sessionId, DpiBenchmarkStore.STATUS_INTERRUPTED, null)
                    restoreOriginalConfig()
                    return
                }

                val strategyStart = System.currentTimeMillis()
                val connected = engine.restartWithStrategy(strategy.id)
                var ready = false
                for (i in 0 until 24) {
                    if (engine.isSocksReady()) { ready = true; break }
                    delay(250)
                }
                if (!ready) {
                    _state.value = DpiBenchmarkState.Preparing("Стратегия ${strategy.id}: ciadpi не поднялся, пропуск")
                    continue
                }
                delay(1_000) // стабилизация + прогрев SOCKS

                var successful = 0
                var failed = 0
                val domainResults = ArrayList<DpiDomainTestResult>(domainOrder.size)

                for ((domainIndex, domain) in domainOrder.withIndex()) {
                    if (stopped) break
                    while (paused && !stopped) delay(300)
                    if (stopped) break

                    _state.value = DpiBenchmarkState.Running(
                        strategyIndex = index + 1,
                        totalStrategies = catalog.size,
                        strategyId = strategy.id,
                        strategyName = strategy.name,
                        domainsChecked = domainIndex,
                        totalDomains = domainOrder.size,
                        successful = successful,
                        failed = failed,
                        currentDomain = domain.domain,
                        currentAttempt = null,
                        elapsedMs = System.currentTimeMillis() - sessionStart,
                        baselineDone = true,
                    )

                    val result = tester.test(domain, BenchRoute.SOCKS)
                    domainResults += result
                    if (result.networkSuccess) successful++ else failed++

                    _state.value = DpiBenchmarkState.Running(
                        strategyIndex = index + 1,
                        totalStrategies = catalog.size,
                        strategyId = strategy.id,
                        strategyName = strategy.name,
                        domainsChecked = domainIndex + 1,
                        totalDomains = domainOrder.size,
                        successful = successful,
                        failed = failed,
                        currentDomain = domain.domain,
                        currentAttempt = result.attempts.lastOrNull(),
                        elapsedMs = System.currentTimeMillis() - sessionStart,
                        baselineDone = true,
                    )
                }

                val strategyResult = DpiStrategyResult(
                    strategyId = strategy.id,
                    strategyName = strategy.name,
                    strategyArgs = strategy.args,
                    connected = connected && ready,
                    domains = domainResults,
                    route = BenchRoute.SOCKS,
                )
                results += strategyResult
                store.insertStrategyResult(sessionId, strategyResult, strategyStart, System.currentTimeMillis())
                store.updateSessionProgress(
                    sessionId,
                    results.size,
                    DpiBenchmarkStore.STATUS_RUNNING,
                    DpiStrategyRanker.best(results, baseline)?.strategyId,
                )

                // Периодический DIRECT-контроль: сеть могла измениться за прогон.
                if ((index + 1) % 6 == 0 && index + 1 < catalog.size) {
                    baseline = measureBaseline(tester, domainOrder)
                }
            }
            // Финальный DIRECT-замер.
            baseline = measureBaseline(tester, domainOrder)
        } finally {
            onTestingModeChanged(false)
            if (stopped) {
                store.finishSession(sessionId, DpiBenchmarkStore.STATUS_STOPPED, null)
                restoreOriginalConfig()
                _state.value = DpiBenchmarkState.Failed("Остановлено пользователем. Исходные настройки восстановлены.")
            } else {
                val ranked = DpiStrategyRanker.rank(results, baseline)
                val best = ranked.firstOrNull()
                store.finishSession(sessionId, DpiBenchmarkStore.STATUS_FINISHED, best?.strategyId)
                restoreOriginalConfig()
                _state.value = DpiBenchmarkState.Finished(sessionId, ranked, baseline, best)
            }
        }
    }

    /** DIRECT-baseline: те же домены и тот же клиент, но напрямую (без SOCKS/ciadpi). */
    private suspend fun measureBaseline(
        tester: DpiDomainTester,
        domains: List<DpiTestDomain>,
    ): Map<String, DpiDomainTestResult> {
        _state.value = DpiBenchmarkState.Preparing("Замер DIRECT (без обхода) — базовая линия")
        val map = LinkedHashMap<String, DpiDomainTestResult>()
        for (domain in domains) {
            if (stopped) break
            map[domain.id] = tester.test(domain, BenchRoute.DIRECT)
        }
        // Baseline — отдельный тип результата в журнале, а не «стратегия с пустыми аргументами».
        val baselineResult = DpiStrategyResult(
            strategyId = "DIRECT",
            strategyName = "Прямое подключение (baseline)",
            strategyArgs = "",
            connected = true,
            domains = map.values.toList(),
            route = BenchRoute.DIRECT,
        )
        store.insertStrategyResult(sessionId, baselineResult, System.currentTimeMillis(), System.currentTimeMillis())
        return map
    }

    /** Возврат исходной конфигурации стратегии (и перезапуск ciadpi с ней). */
    private suspend fun restoreOriginalConfig() = withContext(Dispatchers.IO) {
        prefs.dpiStrategyId = originalStrategyId.ifBlank { prefs.dpiStrategyId }
        prefs.dpiCustomStrategy = originalCustomArgs
        prefs.dpiMapDns = originalMapDns
        runCatching { engine.restartWithCurrentStrategy() }
    }

    /** Контрольный домен: прямой TCP до 1.1.1.1:443 из нашего uid. */
    private fun controlProbe(): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("1.1.1.1", 443), 3_000) }
        true
    } catch (_: Exception) {
        false
    }

    private fun currentNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun carrierName(): String = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        @Suppress("DEPRECATION")
        tm.networkOperatorName.orEmpty()
    }.getOrDefault("")

    private fun appVersion(): String = runCatching {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pkg.versionName} (${pkg.versionCode})"
    }.getOrDefault("")

    private fun strategyOrderPreview(catalog: List<DpiStrategy>): String = catalog.joinToString(",") { it.id }
}
