package svoboda.runet.abonent0007.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import svoboda.runet.abonent0007.AppGraph

/**
 * Восстановление VPN после перезагрузки устройства.
 * Если пользователь подключался и не отключался явно — подключаемся снова.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return
        if (!AppGraph.prefs.shouldReconnect || !AppGraph.prefs.autoConnect) return
        android.util.Log.i("SvobodaBoot", "BOOT_COMPLETED: восстанавливаем VPN")
        AppGraph.appScope.launch { AppGraph.manager.autoConnectOnce() }
    }
}
