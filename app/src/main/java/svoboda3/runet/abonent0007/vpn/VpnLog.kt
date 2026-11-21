package svoboda3.runet.abonent0007.vpn

import android.util.Log

/**
 * Минимальное instrumentation-логирование для диагностики: только логи, без изменения поведения.
 *
 * TODO(B2): заменить на android.util.Log при рефакторинге; пока — отдельный tag SvobodaVpn.
 */
object VpnLog {
    const val TAG = "SvobodaVpn"

    fun i(msg: String) = Log.d(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)
}
