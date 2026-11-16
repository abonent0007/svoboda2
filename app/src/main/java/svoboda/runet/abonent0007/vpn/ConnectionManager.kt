package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import svoboda.runet.abonent0007.AppGraph
import svoboda.runet.abonent0007.data.Prefs
import svoboda.runet.abonent0007.own.OwnServer
import svoboda.runet.abonent0007.own.OwnServerStore
import svoboda.runet.abonent0007.warp.BundledSeed
import svoboda.runet.abonent0007.warp.GeneratedProfile
import svoboda.runet.abonent0007.warp.GeneratedProfileStore
import svoboda.runet.abonent0007.warp.SeedCatalog
import svoboda.runet.abonent0007.net.NetProbe
import svoboda.runet.abonent0007.net.TelegramDc
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

    private fun markServerStatus(server: OwnServer, ok: Boolean) {
        serverStore.update(
            server.copy(
                lastStatus = if (ok) "ok" else "fail",
                lastCheckedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Ручное подключение: свой сервер, иначе новый WARP-профиль. */
    suspend fun connectManual(): Boolean {
        if (prefs.connectionMethod == "AUTO") {
            return autoConnect()
        }
        if (prefs.connectionMethod == "DPI_DIRECT") {
            controller.currentProfileId = "dpi"
            _profileLabel.value = if (prefs.dpiCustomStrategy.isNotBlank()) "DPI · пользовательская" else "DPI · ${prefs.dpiStrategyId}"
            return AppGraph.dpi.start()
        }
        activeServer()?.let { server ->
            controller.currentProfileId = server.id
            _profileLabel.value = serverLabel(server)
            val ok = controller.connect(server.resolvedConfig)
            markServerStatus(server, ok)
            return ok
        }
        return connectWarpWithGeneration()
    }

    private suspend fun connectWarpWithGeneration(): Boolean {
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
                    return true
                }
            }
        }

        // Шаг 3. Откат к началу ранее сгенерированного списка.
        store.list().take(MAX_FALLBACK_PROFILES).forEachIndexed { index, profile ->
            _profileLabel.value = "Профиль WARP · №${index + 1}"
            if (controller.connect(profile.config)) {
                rememberGood(profile.config)
                return true
            }
        }

        // Шаг 4. Последний рубеж — профиль №11 из assets.
        val seed = seedForParams ?: return false
        _profileLabel.value = "Профиль №${seed.number} · WARP"
        val ok = controller.connect(seed.raw_config)
        if (ok) rememberGood(seed.raw_config)
        return ok
    }

    /** Автопереподключение: без генерации, активный сервер или последний удачный конфиг. */
    suspend fun connectLastKnown(): Boolean {
        if (prefs.connectionMethod == "AUTO") {
            return autoConnect()
        }
        if (prefs.connectionMethod == "DPI_DIRECT") {
            controller.currentProfileId = "dpi"
            _profileLabel.value = if (prefs.dpiCustomStrategy.isNotBlank()) "DPI · пользовательская" else "DPI · " + prefs.dpiStrategyId
            return AppGraph.dpi.start()
        }
        activeServer()?.let { server ->
            controller.currentProfileId = server.id
            _profileLabel.value = serverLabel(server)
            val ok = controller.connect(server.resolvedConfig)
            markServerStatus(server, ok)
            return ok
        }
        val config = lastGoodConfig ?: store.list().lastOrNull()?.config
            ?: SeedCatalog.defaultSeed(seeds)?.raw_config
        controller.currentProfileId = "warp"
        if (config != null && controller.connect(config)) return true
        // Сохранённый конфиг не прошёл (устаревшая WARP-регистрация/смена сети):
        // перерегистрируемся через Cloudflare, а не зацикливаемся на мёртвом конфиге.
        return connectWarpWithGeneration()
    }

    /** Переподключение с текущим конфигом (для применения настроек). */
    suspend fun reconnectLastKnown(): Boolean {
        controller.disconnect()
        return connectLastKnown()
    }

    /** Однократное автоподключение при старте приложения (если включено). */
    suspend fun autoConnectOnce(): Boolean {
        if (autoAttempted) return false
        autoAttempted = true
        if (!prefs.autoConnect || controller.phase.value == VpnPhase.CONNECTED) return false
        _profileLabel.value = activeServer()?.let { serverLabel(it) }
            ?: prefs.lastGoodLabel.ifBlank { defaultLabel() }
        val ok = connectLastKnown()
        if (ok && activeServer() == null) prefs.lastGoodConfig = lastGoodConfig.orEmpty()
        return ok
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

    /** AUTO v1: подбор транспорта по приоритету стран и порядку транспортов.
     *  Переключение по foreground-приложению НЕ делаем (ломает активные сессии) —
     *  выбираем один транспорт и держим его (антидребезг: autoHoldMinutes). */
    suspend fun autoConnect(): Boolean {
        val now = System.currentTimeMillis()
        if (prefs.autoHoldMinutes > 0 && controller.isConnected &&
            now - lastAutoSwitchAt < prefs.autoHoldMinutes * 60_000L
        ) {
            return true
        }

        val transports = prefs.autoTransportOrder.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val countries = prefs.autoCountryPriority.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val strict = prefs.autoCountryStrict

        val candidates = mutableListOf<suspend () -> Boolean>()

        transports.forEach { transport ->
            when (transport) {
                "AWG" -> {
                    val servers = serverStore.list()
                    countries.forEach { cc ->
                        servers.filter { it.countryCode.trim().uppercase() == cc }.forEach { srv ->
                            candidates += {
                                controller.currentProfileId = srv.id
                                _profileLabel.value = "AUTO · ${srv.countryName.ifBlank { srv.countryCode }} (AWG)"
                                controller.connect(srv.resolvedConfig)
                            }
                        }
                    }
                    servers.forEach { srv ->
                        candidates += {
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
                            candidates += { connectGeneratedProfile(prof) }
                        }
                    }
                    candidates += { connectWarpWithGeneration() }
                }
                "DPI" -> {
                    candidates += {
                        controller.currentProfileId = "dpi"
                        _profileLabel.value = "AUTO · DPI"
                        AppGraph.dpi.start()
                    }
                }
            }
        }

        if (strict && candidates.isEmpty()) {
            _profileLabel.value = "AUTO · нет профиля нужной страны"
            return false
        }

        var dpiFallback = false
        for (candidate in candidates) {
            if (!candidate()) continue
            val isDpi = controller.currentProfileId == "dpi"
            if (isDpi && prefs.autoTelegramViaVpn) {
                // Telegram-приложение ходит к IP-адресам DC: DPI-стратегии их не открывают.
                // Если DC недоступны — ищем транспорт AWG/WARP, и только потом откатываемся на DPI.
                val dc = TelegramDc.check(viaSocks = true, socksPort = prefs.dpiSocksPort)
                if (!dc.reachable) {
                    autoStatus = "Telegram DC недоступны через DPI (${dc.error ?: "нет ответа"}) — ищем AWG/WARP"
                    if (!dpiFallback) {
                        dpiFallback = true
                        continue
                    }
                } else {
                    autoStatus = "Telegram DC доступны"
                }
            }
            lastAutoSwitchAt = System.currentTimeMillis()
            return true
        }
        if (dpiFallback) {
            autoStatus = "Telegram DC недоступны: подключён DPI (AWG/WARP не найден)"
        }
        return false
    }

    /** Статус AUTO для UI (последняя причина выбора/отказа). */
    @Volatile
    var autoStatus: String? = null

    /** Время последнего автоматического переключения (антидребезг). */
    private var lastAutoSwitchAt: Long = 0L

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
