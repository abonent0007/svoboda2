package svoboda.runet.abonent0007.dpi.benchmark

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.net.InetSocketAddress
import java.net.Socket
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
    ) : DpiBenchmarkState

    data class Paused(val running: Running, val reason: String) : DpiBenchmarkState

    data class Finished(
        val sessionId: Long,
        val results: List<DpiStrategyResult>,
        val best: DpiStrategyResult?,
        val appliedStrategyId: String? = null,
    ) : DpiBenchmarkState

    data class Failed(val reason: String) : DpiBenchmarkState
}

/**
 * Менеджер сессии бенчмарка.
 *
 * Ключевое решение: между стратегиями перезапускается ТОЛЬКО ciadpi
 * (Android VPNService и HEV остаются поднятыми → нет системных разрывов).
 * Исходная конфигурация стратегии снимается до старта и восстанавливается
 * при остановке/ошибке.
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

        val networkType = currentNetworkType()
        sessionId = store.createSession(
            startedAt = System.currentTimeMillis(),
            networkType = networkType,
            mapdnsEnabled = prefs.dpiMapDns,
            selectedDomains = domains.map { it.id },
            totalStrategies = DPI_CATALOG.size,
            originalStrategyId = originalStrategyId,
            originalCustomArgs = originalCustomArgs,
            originalMapdns = originalMapDns,
        )
        onTestingModeChanged(true)

        val tester = DpiDomainTester(socksPort = prefs.dpiSocksPort.coerceIn(1, 65535))
        val results = ArrayList<DpiStrategyResult>()
        val sessionStart = System.currentTimeMillis()

        try {
            for ((index, strategy) in DPI_CATALOG.withIndex()) {
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
                    _state.value = DpiBenchmarkState.Failed("Стратегия ${strategy.id}: ciadpi не поднялся")
                    continue
                }
                delay(1_000) // стабилизация

                val shuffled = domains.shuffled(Random(index * 31L + 7L))
                var successful = 0
                var failed = 0
                val domainResults = ArrayList<DpiDomainTestResult>(shuffled.size)

                for ((domainIndex, domain) in shuffled.withIndex()) {
                    if (stopped) break
                    while (paused && !stopped) delay(300)
                    if (stopped) break

                    _state.value = DpiBenchmarkState.Running(
                        strategyIndex = index + 1,
                        totalStrategies = DPI_CATALOG.size,
                        strategyId = strategy.id,
                        strategyName = strategy.name,
                        domainsChecked = domainIndex,
                        totalDomains = shuffled.size,
                        successful = successful,
                        failed = failed,
                        currentDomain = domain.domain,
                        currentAttempt = null,
                        elapsedMs = System.currentTimeMillis() - sessionStart,
                    )

                    val result = tester.test(domain)
                    domainResults += result
                    if (result.success) successful++ else failed++

                    _state.value = DpiBenchmarkState.Running(
                        strategyIndex = index + 1,
                        totalStrategies = DPI_CATALOG.size,
                        strategyId = strategy.id,
                        strategyName = strategy.name,
                        domainsChecked = domainIndex + 1,
                        totalDomains = shuffled.size,
                        successful = successful,
                        failed = failed,
                        currentDomain = domain.domain,
                        currentAttempt = result.attempts.lastOrNull(),
                        elapsedMs = System.currentTimeMillis() - sessionStart,
                    )
                }

                val strategyResult = DpiStrategyResult(
                    strategyId = strategy.id,
                    strategyName = strategy.name,
                    strategyArgs = strategy.args,
                    connected = connected && ready,
                    domains = domainResults,
                )
                results += strategyResult
                store.insertStrategyResult(sessionId, strategyResult, strategyStart, System.currentTimeMillis())
                store.updateSessionProgress(
                    sessionId,
                    results.size,
                    DpiBenchmarkStore.STATUS_RUNNING,
                    DpiStrategyRanker.best(results)?.strategyId,
                )
            }
        } finally {
            onTestingModeChanged(false)
            if (stopped) {
                store.finishSession(sessionId, DpiBenchmarkStore.STATUS_STOPPED, null)
                restoreOriginalConfig()
                _state.value = DpiBenchmarkState.Failed("Остановлено пользователем. Исходные настройки восстановлены.")
            } else {
                val ranked = DpiStrategyRanker.rank(results)
                val best = ranked.firstOrNull()
                store.finishSession(sessionId, DpiBenchmarkStore.STATUS_FINISHED, best?.strategyId)
                restoreOriginalConfig()
                _state.value = DpiBenchmarkState.Finished(sessionId, ranked, best)
            }
        }
    }

    /** Возврат исходной конфигурации стратегии (и перезапуск ciadpi с ней). */
    private suspend fun restoreOriginalConfig() = withContext(Dispatchers.IO) {
        prefs.dpiStrategyId = originalStrategyId.ifBlank { prefs.dpiStrategyId }
        prefs.dpiCustomStrategy = originalCustomArgs
        prefs.dpiMapDns = originalMapDns
        runCatching { engine.restartWithCurrentStrategy() }
    }

    /** Контрольный домен: прямой TCP из нашего uid (мимо туннеля) — проверка живости сети. */
    private fun controlProbe(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("1.1.1.1", 443), 3_000)
            true
        }
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
}
