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

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", false)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    /** MTU туннеля (1200..1480), по умолчанию 1420. */
    var tunnelMtu: Int
        get() = prefs.getInt("tunnel_mtu", 1420).coerceIn(1_200, 1_480)
        set(value) = prefs.edit().putInt("tunnel_mtu", value.coerceIn(1_200, 1_480)).apply()

    /** Включить IPv6 через туннель (по умолчанию выкл). */
    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6_enabled", false)
        set(value) = prefs.edit().putBoolean("ipv6_enabled", value).apply()

    /** DNS-серверы через запятую; пусто — оставить из конфига. */
    var dnsServers: String
        get() = prefs.getString("dns_servers", "").orEmpty()
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
     * Хранится как имя enum ConnectionMethod; неизвестное значение → AWG.
     */
    var connectionMethod: String
        get() = prefs.getString("connection_method", "AWG").orEmpty()
        set(value) = prefs.edit().putString("connection_method", value).apply()

    /**
     * Автообновление: при старте проверять GitHub Releases, скачивать APK
     * и передавать его системному установщику.
     */
    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean("auto_update_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_update_enabled", value).apply()

    /** Выбранная стратегия ByeDPI (BDP-00 — эталон). */
    var dpiStrategyId: String
        get() = prefs.getString("dpi_strategy_id", "BDP-00").orEmpty()
        set(value) = prefs.edit().putString("dpi_strategy_id", value).apply()

    /** Порт локального SOCKS5 для DPI-движка. */
    var dpiSocksPort: Int
        get() = prefs.getInt("dpi_socks_port", 1080).coerceIn(1, 65535)
        set(value) = prefs.edit().putInt("dpi_socks_port", value.coerceIn(1, 65535)).apply()

    /** Стратегия, собранная генератором (argv для ciadpi). */
    var dpiCustomStrategy: String
        get() = prefs.getString("dpi_custom_strategy", "").orEmpty()
        set(value) = prefs.edit().putString("dpi_custom_strategy", value).apply()

    /** AUTO: предпочитать WARP (иначе AWG). */
    var autoPreferWarp: Boolean
        get() = prefs.getBoolean("auto_prefer_warp", false)
        set(value) = prefs.edit().putBoolean("auto_prefer_warp", value).apply()

    /** AUTO: переключаться на резервный транспорт при отказе. */
    var autoFallback: Boolean
        get() = prefs.getBoolean("auto_fallback", true)
        set(value) = prefs.edit().putBoolean("auto_fallback", value).apply()
}
