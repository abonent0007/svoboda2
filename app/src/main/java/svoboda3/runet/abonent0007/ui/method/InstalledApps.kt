package svoboda3.runet.abonent0007.ui.method

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Установленные приложения устройства (для per-app раздельного туннелирования). */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

object InstalledApps {

    /**
     * Все установленные приложения, кроме собственного пакета.
     * Системные — в конец списка (пользовательские сверху).
     * Требует QUERY_ALL_PACKAGES (см. AndroidManifest) для полноты на Android 11+.
     */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .map { app: ApplicationInfo ->
                    InstalledApp(
                        packageName = app.packageName,
                        label = runCatching { pm.getApplicationLabel(app).toString() }
                            .getOrNull()
                            ?.ifBlank { app.packageName }
                            ?: app.packageName,
                        system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedWith(compareBy({ it.system }, { it.label.lowercase() }))
        }.getOrDefault(emptyList())
    }
}
