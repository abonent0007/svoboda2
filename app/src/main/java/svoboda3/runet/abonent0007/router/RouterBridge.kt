package svoboda3.runet.abonent0007.router

import android.content.Context
import android.util.Log

/**
 * Мост к userspace-ядру мульти-роутинга (mihomo / sing-box / Clash).
 *
 * СТАТУС: заглушка. Реальное ядро не собрано и не подключено.
 * Интеграция — только через VpnController.applySplitTunnel(), см. _plan в docs/SVOBODA3.md.
 *
 * Обязательные ограничения (иначе приложение потеряет работоспособность):
 *  - Android разрешает только ОДИН активный VpnService на пользователя. Поэтому ядро не может
 *    построить свой tun параллельно с текущим DPI/AWG/WARP туннелем: либо замена, либо тот же TUN.
 *  - Per-UID маршрутизация ("каждому приложению свой upstream") возможна только если ядро само
 *    принимает пакеты из TUN и решает по UID/пакету. VpnService allow/deny-списки — один список на туннель.
 *  - Любой новый путь маршрутизации добавлять за флагом и с мгновенным откатом на текущую схему.
 */
object RouterBridge {

    private const val TAG = "Svoboda3Router"

    /** true, если ядро собрано и реально принимает TUN fd. */
    val available: Boolean = false

    /**
     * Запуск ядра с конфигом и существующим TUN fd.
     * @return true при успешном старте. В заглушке всегда false.
     */
    fun start(context: Context, config: String, tunFd: Int): Boolean {
        Log.w(TAG, "RouterBridge.start: ядро не собрано (заглушка) — используйте текущую схему")
        return false
    }

    fun stop() {
        Log.w(TAG, "RouterBridge.stop: ядро не собрано (заглушка)")
    }

    /** Счётчики для UI (при реальном ядре — из него). */
    fun stats(): LongArray = longArrayOf(0, 0, 0, 0)
}
