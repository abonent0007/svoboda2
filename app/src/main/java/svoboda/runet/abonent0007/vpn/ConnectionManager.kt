package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.own.PublicServerCatalog
import svoboda.runet.abonent0007.own.OwnServerStore
import svoboda.runet.abonent0007.warp.BundledSeed
import svoboda.runet.abonent0007.warp.GeneratedProfile
import svoboda.runet.abonent0007.warp.GeneratedProfileStore
import svoboda.runet.abonent0007.warp.SeedCatalog
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.net.TelegramDc
import svoboda.runet.abonent0007.net.DataPlane
import svoboda.runet.abonent0007.split.ServiceCatalog
import svoboda.runet.abonent0007.split.SplitTunnelConfig
import svoboda.runet.abonent0007.split.SplitTunnelMode
import svoboda.runet.abonent0007.warp.WarpGenerator

/**
 * Оркестратор подключения.
 *
 * Если выбран собственный сервер — подключаемся его конфигом без
 * генерации WARP. Иначе ручное подключение генерирует НОВЫЙ WARP-профиль,
 * при неудаче откатывается к началу списка, затем к профилю №11.
 */
class ConnectionManager(
    private val controller: VpnController,
    private val store: GeneratedProfileStore,
    private val seeds: List<BundledSeed>,
    private val prefs: Prefs,
    private val serverStore: OwnServerStore,
) {

    companion object {
        /** Прямые anycast-IP Cloudflare WARP (без DNS — он в РФ подменяется). */
        private val FALLBACK_ENDPOINTS = listOf(
            "162.159.192.1" to 2408,
            "162.159.193.10" to 2408,
            "162.159.192.2" to 2408,
        )
        private const val MAX_FALLBACK_PROFILES = 5
    }

    private val _profileLabel = MutableStateFlow(defaultLabel())
    val profileLabel: StateFlow<String> = _profileLabel.asStateFlow()

    /** Кэш последнего удачного конфига — для автопереподключения. */
    private var lastGoodConfig: String? = prefs.lastGoodConfig.takeIf { it.isNotBlank() }

    private var autoAttempted = false

    fun defaultLabel(): String {
        val seed = SeedCatalog.defaultSeed(seeds)
        return "Профиль №${seed?.number ?: SeedCatalog.DEFAULT_PROFILE_NUMBER} · WARP"
    }

    private fun rememberGood(config: String) {
        lastGoodConfig = config
        prefs.lastGoodConfig = config
        prefs.lastGoodLabel = _profileLabel.value
    }

    /** Активный собственный сервер (null — работаем через WARP). */
    fun activeServer(): OwnServer? =
        serverStore.list().firstOrNull { it.id == prefs.activeServerId }

    /** Подпись локации: страна сервера ≠ страна внешнего IP. */
    fun serverLabel(server: OwnServer): String {
        val head = server.locationTitle
        return if (head.isBlank()) "Свой сервер · AWG" else "$head · AWG"
    }

    /**
     * Валидация data-plane для любого выбранного транспорта.
     * AWG/WARP/публичные — через собственный пакет (идёт через tun0),
     * DPI — через локальный SOCKS5 (ciadpi). Одна контрольная попытка при двойном провале.
     */
    private suspend fun validateDataPlane(candidateId: String): ConnectionResult {
        // Для DPI единственный честный data-plane — SOCKS5 (ciadpi). Если порт ещё не
        // выставлен движком, берём сконфигурированный.
        val socksPort = controller.dpiProbePort
            ?: prefs.dpiSocksPort.takeIf { candidateId == "dpi" || controller.currentProfileId == "dpi" }
        if (socksPort != null) {
            return socksDataPlaneResult(candidateId, socksPort)
        }
        var outcome = DataPlane.validate()
        if (!DataPlane.isUsable(outcome)) {
            logAuto("candidate $candidateId: data-plane first probe failed, control retry")
            kotlinx.coroutines.delay(2500)
            outcome = DataPlane.validate()
        }
        return when {
            DataPlane.isUsable(outcome) -> {
                val partial = !(outcome.cf.ok && outcome.g.ok)
                if (partial) {
                    logAuto("candidate $candidateId: data-plane ok (partial: ${if (outcome.cf.ok) "cloudflare" else "gstatic"})")
                } else {
                    logAuto("candidate $candidateId: data-plane ok")
                }
                ConnectionResult.ConnectedHealthy
            }
            else -> {
                val kind = DataPlane.classify(outcome)
                val detail = classifyFailure(kind, outcome.cf.httpCode ?: outcome.g.httpCode, outcome.cf.detail + " | " + outcome.g.detail)
                logAuto("candidate $candidateId: CANDIDATE_DEGRADED ($detail)")
                runCatching { controller.disconnect() }
                ConnectionResult.CandidateDegraded(candidateId, kind, detail)
            }
        }
    }

    private suspend fun socksDataPlaneResult(candidateId: String, port: Int): ConnectionResult {
        val exit = NetProbe.fetchExitViaSocks(port)
        return if (exit != null) {
            logAuto("candidate $candidateId: data-plane ok (socks)")
            ConnectionResult.ConnectedHealthy
        } else {
            logAuto("candidate $candidateId: CANDIDATE_DEGRADED (stage=socks)")
            ConnectionResult.CandidateDegraded(candidateId, FailureKind.UNKNOWN, "socks probe failed on 127.0.0.1:$port")
        }
    }

    private fun markServerStatus(server: OwnServer, ok: Boolean) {
        serverStore.update(
            server.copy(
                lastStatus = if (ok) "ok" else "fail",
                lastCheckedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Ручное подключение: свой сервер, иначе новый WARP-профиль. */
    suspend fun connectManual(): ConnectionResult {
        if (prefs.connectionMethod == "AUTO") {
            return autoConnect(force = true)
        }
        if (prefs.connectionMethod == "DPI_DIRECT") {
            controller.currentProfileId = "dpi"
            _profileLabel.value = if (prefs.dpiCustomStrategy.isNotBlank()) "DPI · пользовательская" else "DPI · ${prefs.dpiStrategyId}"
            if (!AppGraph.dpi.start()) {
                logAuto("candidate dpi: TRANSPORT_FAIL(stage=dpi-start)")
                return ConnectionResult.CandidateDegraded("dpi", FailureKind.UNKNOWN, "dpi engine start failed")
            }
            return validateDataPlane("dpi")
        }
        activeServer()?.let { server ->
            controller.currentProfileId = server.id
            _profileLabel.value = serverLabel(server)
            val ok = controller.connect(server.resolvedConfig)
            markServerStatus(server, ok)
            if (!ok) {
                logAuto("candidate ${server.id}: TRANSPORT_FAIL(stage=handshake, awg connect failed)")
                return ConnectionResult.CandidateDegraded(server.id, FailureKind.HANDSHAKE, "awg connect failed")
            }
            return validateDataPlane(server.id)
        }
        return connectWarpWithGeneration()
    }

    private suspend fun connectWarpWithGeneration(): ConnectionResult {
        controller.currentProfileId = "warp"
        applyServiceDirect("warp")
        // Jc/Jmin/Jmax и I1 берём из проверенного профиля №11: та же
        // обфускация, что уже проходит DPI.
        val seedForParams = SeedCatalog.defaultSeed(seeds)
        val seedRaw = seedForParams?.raw_config.orEmpty()
        val i1 = Regex("(?m)^\\s*I1\\s*=\\s*(.+)$").find(seedRaw)?.groupValues?.get(1).orEmpty()

        // Шаг 1. Поднимаем проверенный профиль: без него API Cloudflare
        // в РФ недоступен (DNS/DPI), а через туннель регистрация проходит.
        val bootstrap = lastGoodConfig ?: seedRaw
        if (bootstrap.isNotBlank()) {
            _profileLabel.value = "Профиль №${seedForParams?.number ?: SeedCatalog.DEFAULT_PROFILE_NUMBER} · WARP"
            controller.connect(bootstrap)
        }

        // Шаг 2. Через поднятый туннель регистрируем НОВЫЙ WARP-профиль.
        val identity = WarpGenerator.register()
        if (identity != null) {
            val endpoints = buildList {
                if (identity.endpointV4.isNotBlank()) add(identity.endpointV4 to identity.endpointPort)
                addAll(FALLBACK_ENDPOINTS)
            }.distinct()
            endpoints.forEachIndexed { index, (host, port) ->
                _profileLabel.value = if (index == 0) {
                    "Профиль WARP · новый"
                } else {
                    "Профиль WARP · новый (${index + 1})"
                }
                val config = WarpGenerator.buildConfig(identity, host, port, i1 = i1)
                store.add(GeneratedProfile(System.currentTimeMillis(), host, port, config))
                if (controller.connect(config)) {
                    rememberGood(config)
                    return validateDataPlane("warp:generated")
                }
            }
        }

        // Шаг 3. Откат к началу ранее сгенерированного списка.
        store.list().take(MAX_FALLBACK_PROFILES).forEachIndexed { index, profile ->
            _profileLabel.value = "Профиль WARP · №${index + 1}"
            if (controller.connect(profile.config)) {
                rememberGood(profile.config)
                return validateDataPlane("warp:fallback-${index + 1}")
            }
        }

        // Шаг 4. Последний рубеж — профиль №11 из assets.
        val seed = seedForParams ?: return ConnectionResult.CandidateDegraded("warp", FailureKind.UNKNOWN, "no seeds bundled")
        _profileLabel.value = "Профиль №${seed.number} · WARP"
        val ok = controller.connect(seed.raw_config)
        if (!ok) {
            logAuto("candidate warp:seed${seed.number}: TRANSPORT_FAIL(stage=handshake)")
            return ConnectionResult.CandidateDegraded("warp:seed", FailureKind.HANDSHAKE, "bootstrap seed connect failed")
        }
        rememberGood(seed.raw_config)
        return validateDataPlane("warp:seed${seed.number}")
    }

    /** Автопереподключение: без генерации, активный сервер или последний удачный конфиг.
     *  @param force true — пропустить антидребезг (ручное ПОДКЛЮЧИТЬ/failover с деградированного маршрута). */
    suspend fun connectLastKnown(force: Boolean = false): ConnectionResult {
        if (prefs.connectionMethod == "AUTO") {
            return autoConnect(force = force)
        }
        if (prefs.connectionMethod == "DPI_DIRECT") {
            controller.currentProfileId = "dpi"
            _profileLabel.value = if (prefs.dpiCustomStrategy.isNotBlank()) "DPI · пользовательская" else "DPI · " + prefs.dpiStrategyId
            if (!AppGraph.dpi.start()) {
                logAuto("candidate dpi: TRANSPORT_FAIL(stage=dpi-start)")
                return ConnectionResult.CandidateDegraded("dpi", FailureKind.UNKNOWN, "dpi engine start failed")
            }
            return validateDataPlane("dpi")
        }
        activeServer()?.let { server ->
            controller.currentProfileId = server.id
            _profileLabel.value = serverLabel(server)
            val ok = controller.connect(server.resolvedConfig)
            markServerStatus(server, ok)
            if (!ok) {
                logAuto("candidate ${server.id}: TRANSPORT_FAIL(stage=handshake, awg connect failed)")
                return ConnectionResult.CandidateDegraded(server.id, FailureKind.HANDSHAKE, "awg connect failed")
            }
            return validateDataPlane(server.id)
        }
        val config = lastGoodConfig ?: store.list().lastOrNull()?.config
            ?: SeedCatalog.defaultSeed(seeds)?.raw_config
        controller.currentProfileId = "warp"
        if (config != null && controller.connect(config)) {
            return validateDataPlane("warp:lastgood")
        }
        // Сохранённый конфиг не прошёл (устаревшая WARP-регистрация/смена сети):
        // перерегистрируемся через Cloudflare, а не зацикливаемся на мёртвом конфиге.
        return connectWarpWithGeneration()
    }

    /** Переподключение с текущим конфигом (для применения настроек). */
    suspend fun reconnectLastKnown(): ConnectionResult {
        controller.disconnect()
        return connectLastKnown(force = true)
    }

    /** Однократное автоподключение при старте приложения (если включено). */
    suspend fun autoConnectOnce(): Boolean {
        if (autoAttempted) return false
        autoAttempted = true
        if (!prefs.autoConnect || controller.phase.value == VpnPhase.CONNECTED) return false
        _profileLabel.value = activeServer()?.let { serverLabel(it) }
            ?: prefs.lastGoodLabel.ifBlank { defaultLabel() }
        val ok = connectLastKnown()
        if (ok is ConnectionResult.ConnectedHealthy && activeServer() == null) {
            prefs.lastGoodConfig = lastGoodConfig.orEmpty()
            return true
        }
        return false
    }

    /** Выбирает собственный сервер и применяет его (с переподключением). */
    suspend fun selectServer(server: OwnServer): Boolean {
        prefs.activeServerId = server.id
        _profileLabel.value = serverLabel(server)
        controller.disconnect()
        val ok = controller.connect(server.config)
        markServerStatus(server, ok)
        return ok
    }

    /** Возврат на WARP-профиль по умолчанию. */
    suspend fun useWarp(): Boolean {
        prefs.activeServerId = ""
        _profileLabel.value = defaultLabel()
        controller.disconnect()
        val config = lastGoodConfig ?: SeedCatalog.defaultSeed(seeds)?.raw_config ?: return false
        return controller.connect(config)
    }

    /**
     * Раздельное туннелирование: выбранные сервисы (YouTube/Telegram/...) идут мимо туннеля.
     * Применяется к конкретному профилю (у каждого WARP-профиля свой набор).
     */
    private fun applyServiceDirect(profileId: String) {
        val ids = prefs.serviceDirectSelection
        if (ids.isEmpty()) return
        val packages = ServiceCatalog.packagesFor(AppGraph.appContext, ids) + ServiceCatalog.fallbackPackages(ids)
        if (packages.isEmpty()) return
        AppGraph.splitManager.update(
            profileId,
            SplitTunnelConfig(
                profileId = profileId,
                mode = SplitTunnelMode.ALL_EXCEPT_SELECTED,
                packageNames = packages,
                presetId = "services_direct",
            ),
        )
    }

    /** Подключиться к встроенному публичному WARP-серверу; страну измеряем после подключения. */
    suspend fun connectPublicServer(id: String): Boolean {
        val server = PublicServerCatalog.byId(id) ?: return false
        val config = PublicServerCatalog.config(AppGraph.appContext, id) ?: return false
        controller.disconnect()
        controller.currentProfileId = "public:$id"
        applyServiceDirect(controller.currentProfileId)
        _profileLabel.value = "${server.title} (публичный)"
        val ok = controller.connect(config)
        if (ok) {
            val exit = NetProbe.fetchExit()
            val cc = exit?.country?.uppercase().orEmpty()
            if (cc.isNotBlank()) {
                val map = prefs.publicServerCountries.toMutableMap()
                map[id] = cc + "@" + System.currentTimeMillis()
                prefs.publicServerCountries = map
            }
        }
        return ok
    }

    /** Подключиться к конкретному сгенерированному WARP-профилю. */
    suspend fun connectGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile): Boolean {
        controller.disconnect()
        controller.currentProfileId = "warp:${profile.host}:${profile.port}"
        applyServiceDirect(controller.currentProfileId)
        _profileLabel.value = "WARP · ${profile.host}:${profile.port}"
        val ok = controller.connect(profile.config)
        if (ok) {
            rememberGood(profile.config)
            // Фактическая страна выхода (для фильтра NL/FI/US) — измеряем, а не гадаем.
            val exit = NetProbe.fetchExit()
            if (exit != null && exit.country.isNotBlank()) {
                store.updateCountry(profile, exit.country.uppercase(), System.currentTimeMillis())
            }
        }
        return ok
    }

    private data class AutoCandidate(val id: String, val run: suspend () -> Boolean)

    /** AUTO: перебор кандидатов с обязательной проверкой data-plane (P0, 2026-11-18). */
    suspend fun autoConnect(force: Boolean = false): ConnectionResult {
        val now = System.currentTimeMillis()
        val hasHealthySession = controller.isConnected && lastAutoSwitchAt > 0L
        val decision = AntiBounceGate.proceed(
            isManualConnect = force,
            candidateDegraded = false,
            hasHealthySession = hasHealthySession,
            lastSwitchAt = lastAutoSwitchAt,
            nowMs = now,
            holdMinutes = prefs.autoHoldMinutes,
        )
        if (decision == Proceed.Deferred) {
            logAuto("anti-bounce: hold ${prefs.autoHoldMinutes}m (no switch)")
            return ConnectionResult.ReconnectDeferred
        }
        logAuto("AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}")

        val transports = prefs.autoTransportOrder.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val countries = prefs.autoCountryPriority.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val strict = prefs.autoCountryStrict

        val candidates = mutableListOf<AutoCandidate>()

        transports.forEach { transport ->
            when (transport) {
                "AWG" -> {
                    val servers = serverStore.list()
                    logAuto("AWG: own servers=${servers.size}")
                    countries.forEach { cc ->
                        servers.filter { it.countryCode.trim().uppercase() == cc }.forEach { srv ->
                            candidates += AutoCandidate("awg:${srv.id}") {
                                controller.currentProfileId = srv.id
                                _profileLabel.value = "AUTO · ${srv.countryName.ifBlank { srv.countryCode }} (AWG)"
                                controller.connect(srv.resolvedConfig)
                            }
                        }
                    }
                    servers.forEach { srv ->
                        candidates += AutoCandidate("awg:${srv.id}") {
                            controller.currentProfileId = srv.id
                            _profileLabel.value = "AUTO · ${srv.countryName.ifBlank { srv.countryCode }} (AWG)"
                            controller.connect(srv.resolvedConfig)
                        }
                    }
                }
                "WARP" -> {
                    val profiles = store.list()
                    countries.forEach { cc ->
                        profiles.filter { it.observedCountry.trim().uppercase() == cc }.forEach { prof ->
                            candidates += AutoCandidate("warp:${prof.host}:${prof.port}") { connectGeneratedProfile(prof) }
                        }
                    }
                    // Встроенные публичные NL/FI/US: приоритет — по измеренной стране
                    // (без измерения идём в порядке метки: NL, FI, затем любые).
                    if (prefs.autoIncludePublicServers) {
                        val publicServers = PublicServerCatalog.servers
                        val measured = prefs.publicServerCountries
                        val priorityIndex: (String) -> Int = { cc ->
                            countries.indexOf(cc.trim().uppercase()).let { index -> if (index < 0) 99 else index }
                        }
                        val allowed = publicServers.filter { pub ->
                            val age = prefs.publicServerCountryAgeDays(pub.id)
                            val cc = (measured[pub.id] ?: "").substringBefore('@').uppercase()
                            val fresh = age in 0..7
                            if (fresh && countries.contains(cc)) true else !strict
                        }
                        allowed.sortedBy { pub ->
                            priorityIndex((measured[pub.id] ?: "").substringBefore('@').uppercase())
                        }.forEach { pub ->
                            candidates += AutoCandidate("public:${pub.id}") { connectPublicServer(pub.id) }
                        }
                        if (allowed.isEmpty()) logAuto("публичные: согласия нет или страна не свежая (>7 дней)")
                    }
                    candidates += AutoCandidate("warp:generated") { connectWarpWithGeneration() is ConnectionResult.ConnectedHealthy }
                }
                "DPI" -> {
                    candidates += AutoCandidate("dpi") {
                        logAuto("DPI candidate")
                        controller.currentProfileId = "dpi"
                        _profileLabel.value = "AUTO · DPI"
                        AppGraph.dpi.start()
                    }
                }
            }
        }

        if (strict && candidates.isEmpty()) {
            logAuto("strict: no candidates — abort")
            _profileLabel.value = "AUTO · нет профиля нужной страны"
            return ConnectionResult.NoWorkingRoute(listOf("strict: no candidates of requested countries"))
        }

        val cycle = CandidateCycle()
        cycle.beginCycle()
        var dpiFallback = false
        for ((i, candidate) in candidates.withIndex()) {
            if (!cycle.canTry(candidate.id, now)) {
                logAuto("try candidate #${i + 1} ${candidate.id}: skipped (quarantine)")
                continue
            }
            logAuto("try candidate #${i + 1} ${candidate.id}")
            val transportOk = candidate.run()
            if (!transportOk) {
                logAuto("candidate ${candidate.id}: TRANSPORT_FAIL(stage=transport)")
                cycle.recordFailure(candidate.id, now, "transport fail")
                continue
            }
            val dp = validateDataPlane(candidate.id)
            if (dp !is ConnectionResult.ConnectedHealthy) {
                val detail = (dp as? ConnectionResult.CandidateDegraded)?.detail ?: "data-plane failed"
                cycle.recordFailure(candidate.id, now, detail)
                logAuto("candidate ${candidate.id}: data-plane rejected -> next candidate")
                continue
            }
            logAuto("candidate #${i + 1} selected: ${controller.currentProfileId}")
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {
                // Telegram-приложение ходит к IP-адресам DC: DPI-стратегии их не открывают.
                // Если DC недоступны — ищем транспорт AWG/WARP, и только потом откатываемся на DPI.
                val dc = TelegramDc.check(viaSocks = true, socksPort = prefs.dpiSocksPort)
                if (!dc.reachable) {
                    logAuto("Telegram DC over DPI down (${dc.error ?: "no answer"}) -> AWG/WARP")
                    autoStatus = "Telegram DC недоступны через DPI (${dc.error ?: "нет ответа"}) — ищем AWG/WARP"
                    if (!dpiFallback) {
                        dpiFallback = true
                        cycle.recordFailure(candidate.id, now, "telegram-dc-down")
                        runCatching { controller.disconnect() }
                        continue
                    }
                } else {
                    logAuto("Telegram DC reachable")
                    autoStatus = "Telegram DC доступны"
                }
            }
            lastAutoSwitchAt = System.currentTimeMillis()
            logAuto("done: profile=${controller.currentProfileId}")
            return ConnectionResult.ConnectedHealthy
        }
        if (dpiFallback) {
            autoStatus = "Telegram DC недоступны: подключён DPI (AWG/WARP не найден)"
        }
        val reasons = cycle.failures()
        logAuto("no working route: candidates rejected: $reasons")
        return ConnectionResult.NoWorkingRoute(reasons)
    }

    /** Статус AUTO для UI (последняя причина выбора/отказа). */
    @Volatile
    var autoStatus: String? = null

    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L
    /** Журнал решений AUTO (одной строкой на событие), хранится локально. */
    @Volatile
    var autoLog: List<String> = loadAutoLog()
        private set

    private fun logAuto(event: String) {
        val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val line = "$stamp $event"
        autoLog = (autoLog + line).takeLast(150)
        runCatching {
            java.io.File(AppGraph.appContext.filesDir, "auto_log.txt").appendText(line + "\n", Charsets.UTF_8)
        }
    }

    private fun loadAutoLog(): List<String> = runCatching {
        val file = java.io.File(AppGraph.appContext.filesDir, "auto_log.txt")
        if (file.exists()) file.readLines(Charsets.UTF_8).takeLast(150) else emptyList()
    }.getOrDefault(emptyList())

    /** Сохранённые сгенерированные WARP-профили (новые сверху). */
    fun generatedProfiles(): List<svoboda.runet.abonent0007.warp.GeneratedProfile> =
        store.list().sortedByDescending { it.createdAt }

    fun addGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile) {
        store.add(profile)
    }

    fun clearGeneratedProfiles() {
        store.clear()
    }

    /** Удалить сгенерированный профиль из хранилища. */
    fun removeGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile) {
        store.remove(profile)
    }

    fun resetLabel() {
        _profileLabel.value = activeServer()?.let { serverLabel(it) } ?: defaultLabel()
    }
}
