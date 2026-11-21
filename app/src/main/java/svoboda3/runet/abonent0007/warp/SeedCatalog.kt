package svoboda3.runet.abonent0007.warp

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Каталог предустановленных WARP-профилей из assets.
 *
 * Профиль по умолчанию — №11 (seed_order 10). Очередь fallback идёт от
 * начала списка (seed_order 0..N), как согласовано.
 */
object SeedCatalog {

    const val ASSET_PATH = "seeds/warp_verified_seeds.json"

    /** 1-based номер профиля по умолчанию. */
    const val DEFAULT_PROFILE_NUMBER = 11

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<BundledSeed> = runCatching {
        val text = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        json.decodeFromString<List<BundledSeed>>(text)
            .sortedBy { it.seed_order }
    }.getOrDefault(emptyList())

    /** Профиль №11; при его отсутствии — первый из списка. */
    fun defaultSeed(seeds: List<BundledSeed>): BundledSeed? =
        seeds.firstOrNull { it.number == DEFAULT_PROFILE_NUMBER } ?: seeds.firstOrNull()
}
