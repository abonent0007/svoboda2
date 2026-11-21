package svoboda3.runet.abonent0007.dpi

import android.util.Log
import svoboda3.runet.abonent0007.net.NetProbe

/**
 * Авто-подбор DPI-стратегии под YouTube / Google-видео из существующего каталога.
 *
 * Проба выполняется БЕЗ туннеля: на время теста поднимается отдельный процесс ciadpi,
 * а проверка идёт через его SOCKS5 ([DpiEngine.testStrategy]). Поэтому подбор можно
 * запускать до подключения и он не рвёт текущую сессию.
 */
object DpiStrategyAutoSelect {

    private const val TAG = "SvobodaDpiAuto"

    /** Цели: YouTube + медиа-CDN Google (ytimg/googlevideo). */
    val TARGETS = listOf(
        "https://www.youtube.com/generate_204",
        "https://i.ytimg.com/generate_204",
    )

    data class Progress(val index: Int, val total: Int, val id: String)

    /**
     * Перебирает каталог по порядку, возвращает id первой стратегии,
     * открывающей обе цели, или null (тогда текущая стратегия остаётся).
     */
    suspend fun pick(
        engine: DpiEngine,
        onProgress: (Progress) -> Unit = {},
    ): String? {
        val ids = DPI_CATALOG.map { it.id }
        for ((i, id) in ids.withIndex()) {
            onProgress(Progress(i + 1, ids.size, id))
            val args = dpiStrategyById(id)?.args
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?.toTypedArray()
                ?: continue
            val ok = engine.testStrategy(args) { port ->
                TARGETS.any { NetProbe.probeViaSocks(port, it) }
            }
            if (ok) {
                Log.i(TAG, "youtube strategy ok: $id")
                return id
            }
        }
        Log.w(TAG, "no working youtube strategy among ${ids.size}")
        return null
    }
}
