package svoboda.runet.abonent0007.data

import android.content.Context
import android.content.SharedPreferences

/** Настройки приложения (SharedPreferences-обёртка; DataStore — позже при миграции). */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("svoboda2_prefs", Context.MODE_PRIVATE)

    var pingGraphEnabled: Boolean
        get() = prefs.getBoolean("ping_graph_enabled", true)
        set(value) = prefs.edit().putBoolean("ping_graph_enabled", value).apply()

    /** Интервал пинга, мс (1000..10000). */
    var pingIntervalMs: Int
        get() = prefs.getInt("ping_interval_ms", 2_000).coerceIn(1_000, 10_000)
        set(value) = prefs.edit().putInt("ping_interval_ms", value.coerceIn(1_000, 10_000)).apply()

    /** Автоподключение при запуске (рабочая конфигурация по умолчанию — проверено на RMX3370). */
    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", true)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    /** MTU туннеля (1200..1480), по умолчанию 1440 (рабочая конфигурация). */
    var tunnelMtu: Int
        get() = prefs.getInt("tunnel_mtu", 1440).coerceIn(1_200, 1_480)
        set(value) = prefs.edit().putInt("tunnel_mtu", value.coerceIn(1_200, 1_480)).apply()

    /** IPv6 через туннель (в рабочей конфигурации включён). */
    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6_enabled", true)
        set(value) = prefs.edit().putBoolean("ipv6_enabled", value).apply()

    /** DNS-серверы через запятую; пусто — оставить из конфига. */
    var dnsServers: String
        get() = prefs.getString("dns_servers", DEFAULT_DNS_SERVERS).orEmpty()
        set(value) = prefs.edit().putString("dns_servers", value.trim()).apply()

    /** Последний удачный конфиг — для автопереподключения (хранится зашифрованным). */
    var lastGoodConfig: String
        get() {
            val raw = prefs.getString("last_good_config", "").orEmpty()
            if (raw.isBlank()) return ""
            if (!svoboda.runet.abonent0007.secure.SecureConfigStorage.isEncrypted(raw)) {
                // legacy plaintext -> migrate
                svoboda.runet.abonent0007.secure.SecureConfigStorage.encrypt(raw)?.let { enc ->
                    prefs.edit().putString("last_good_config", enc).apply()
                }
                return raw
            }
            return svoboda.runet.abonent0007.secure.SecureConfigStorage.decrypt(raw).orEmpty()
        }
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove("last_good_config").apply()
                return
            }
            val enc = svoboda.runet.abonent0007.secure.SecureConfigStorage.encrypt(value) ?: value
            prefs.edit().putString("last_good_config", enc).apply()
        }

    /** Подпись профиля, которым реально подключились (для UI после рестарта). */
    var lastGoodLabel: String
        get() = prefs.getString("last_good_label", "").orEmpty()
        set(value) = prefs.edit().putString("last_good_label", value).apply()

    /** Раздельное туннелирование: выбранные приложения идут мимо VPN. */
    var splitTunnelEnabled: Boolean
        get() = prefs.getBoolean("split_tunnel_enabled", false)
        set(value) = prefs.edit().putBoolean("split_tunnel_enabled", value).apply()

    var excludedApps: Set<String>
        get() = prefs.getStringSet("excluded_apps", emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet("excluded_apps", value).apply()

    /** Активный собственный сервер; пусто — WARP по умолчанию. */
    var activeServerId: String
        get() = prefs.getString("active_server_id", "").orEmpty()
        set(value) = prefs.edit().putString("active_server_id", value).apply()

    /**
     * Выбранный способ подключения: AWG | WARP | DPI_DIRECT | AUTO.
     * Хранится как имя enum ConnectionMethod; неизвестное значение → AUTO (рабочая конфигурация).
     */
    var connectionMethod: String
        get() = prefs.getString("connection_method", "AUTO").orEmpty()
        set(value) = prefs.edit().putString("connection_method", value).apply()

    /**
     * Автообновление: при старте проверять GitHub Releases, скачивать APK
     * и передавать его системному установщику.
     * Репозиторий публичный — включено по умолчанию; выключается тумблером в меню.
     */
    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean("auto_update_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_update_enabled", value).apply()

    /** Выбранная стратегия ByeDPI (по умолчанию dpi_x03 — рабочая конфигурация). */
    var dpiStrategyId: String
        get() = prefs.getString("dpi_strategy_id", "dpi_x03").orEmpty()
        set(value) = prefs.edit().putString("dpi_strategy_id", value).apply()

    /** Порт локального SOCKS5 для DPI-движка. */
    var dpiSocksPort: Int
        get() = prefs.getInt("dpi_socks_port", 1080).coerceIn(1, 65535)
        set(value) = prefs.edit().putInt("dpi_socks_port", value.coerceIn(1, 65535)).apply()

    /** Измеренные страны выхода публичных серверов ("id=CC"). */
    var publicServerCountries: Map<String, String>
        get() = (prefs.getStringSet("public_server_countries", emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        set(value) = prefs.edit().putStringSet("public_server_countries", value.map { "${it.key}=${it.value}" }.toSet()).apply()

    /** Возраст последнего измерения страны публичного сервера в днях (-1 если нет). */
    fun publicServerCountryAgeDays(id: String): Long {
        val raw = prefs.getStringSet("public_server_countries", emptySet())
            ?.firstOrNull { it.startsWith("$id=") }
            ?.substringAfter('=', "")
        ?: return -1
        val parts = raw.split('@', limit = 2)
        val ts = parts.getOrNull(1)?.toLongOrNull() ?: return Long.MAX_VALUE
        return (System.currentTimeMillis() - ts) / (24L * 60L * 60L * 1000L)
    }

    /** Число попыток на домен в бенчмарке: 3 — скрининг, 10 — валидация, 20 — финал. */
    var dpiBenchmarkAttempts: Int
        get() = prefs.getInt("dpi_benchmark_attempts", 3).coerceIn(1, 20)
        set(value) = prefs.edit().putInt("dpi_benchmark_attempts", value.coerceIn(1, 20)).apply()

    /** Фильтр стратегий бенчмарка (пусто = весь каталог). Нужен для повторной проверки победителей. */
    var dpiBenchmarkStrategyIds: Set<String>
        get() = prefs.getStringSet("dpi_benchmark_strategy_ids", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("dpi_benchmark_strategy_ids", value).apply()

    /** DNS через туннель (HEV mapdns): домены резолвятся внутри обхода. По умолчанию включён. */
    var dpiMapDns: Boolean
        get() = prefs.getBoolean("dpi_map_dns", true)
        set(value) = prefs.edit().putBoolean("dpi_map_dns", value).apply()

    /** Выбранные домены для бенчмарка DPI (сохраняются между запусками). */
    /** DPI: приложения, чей трафик идёт через туннель (allowlist, пакеты). Пусто — весь трафик. */
    var dpiTunnelApps: Set<String>
        get() = prefs.getStringSet("dpi_tunnel_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("dpi_tunnel_apps", value).apply()

    /** AUTO: приоритет стран выхода (ISO-коды через запятую). По умолчанию US (рабочая конфигурация). */
    var autoCountryPriority: String
        get() = prefs.getString("auto_country_priority", "US") ?: "US"
        set(value) = prefs.edit().putString("auto_country_priority", value).apply()

    /** AUTO: порядок транспортов (AWG,WARP,DPI). */
    var autoTransportOrder: String
        get() = prefs.getString("auto_transport_order", "AWG,WARP,DPI") ?: "AWG,WARP,DPI"
        set(value) = prefs.edit().putString("auto_transport_order", value).apply()

    /** AUTO: строгая политика страны (не уходить в другие страны). По умолчанию включена. */
    var autoCountryStrict: Boolean
        get() = prefs.getBoolean("auto_country_strict", true)
        set(value) = prefs.edit().putBoolean("auto_country_strict", value).apply()

    /** AUTO: использовать встроенные публичные серверы (чужие операторы). По умолчанию включено. */
    var autoIncludePublicServers: Boolean
        get() = prefs.getBoolean("auto_include_public_servers", true)
        set(value) = prefs.edit().putBoolean("auto_include_public_servers", value).apply()

    /** AUTO: если DC Telegram недоступны через DPI — выбирать AWG/WARP. */
    var autoTelegramViaVpn: Boolean
        get() = prefs.getBoolean("auto_telegram_via_vpn", true)
        set(value) = prefs.edit().putBoolean("auto_telegram_via_vpn", value).apply()

    /** AUTO: минимальное время удержания выбранного транспорта, мин (антидребезг). */
    var autoHoldMinutes: Int
        get() = prefs.getInt("auto_hold_minutes", 5).coerceIn(0, 120)
        set(value) = prefs.edit().putInt("auto_hold_minutes", value).apply()

    /**
     * Сервисы, которые направляются мимо туннеля (раздельное туннелирование, WARP).
     * @deprecated оставлено для миграции: заполнялось  id сервисов (whatsapp/viber/...).
     */
    var serviceDirectSelection: Set<String>
        get() = prefs.getStringSet("service_direct_selection", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("service_direct_selection", value).apply()

    /** WARP: приложения, чей трафик идёт через туннель (allowlist, пакеты). */
    var warpTunnelApps: Set<String>
        get() = prefs.getStringSet("warp_tunnel_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("warp_tunnel_apps", value).apply()

    /**
     * Глобальный список приложений мимо VPN (denylist, пакеты).
     * Один на все методы (AWG/WARP/DPI/AUTO) — редактируется из шторки и из настроек WARP.
     */
    var globalDirectApps: Set<String>
        get() = prefs.getStringSet("global_direct_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("global_direct_apps", value).apply()

    var dpiBenchmarkDomains: Set<String>
        get() = prefs.getStringSet("dpi_benchmark_domains", null)
            ?: svoboda.runet.abonent0007.dpi.benchmark.DpiDomainCatalog.defaultSelection
        set(value) = prefs.edit().putStringSet("dpi_benchmark_domains", value).apply()

    /** Стратегия, собранная генератором (argv для ciadpi). */
    var dpiCustomStrategy: String
        get() = prefs.getString("dpi_custom_strategy", "").orEmpty()
        set(value) = prefs.edit().putString("dpi_custom_strategy", value).apply()

    companion object {
        /** Антиблок-резолверы (рабочая конфигурация по умолчанию, проверена на RMX3370). */
        const val DEFAULT_DNS_SERVERS =
            "45.155.204.190, 37.230.192.51, 159.194.200.33, 193.233.112.67, 193.233.112.88"
    }
}
