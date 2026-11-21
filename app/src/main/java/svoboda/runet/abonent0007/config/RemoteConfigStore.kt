package svoboda.runet.abonent0007.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import svoboda.runet.abonent0007.AppGraph
import java.io.File
import java.util.concurrent.TimeUnit

private val IP_RX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

/**
 * Внешняя конфигурация вместо хардкода (минимальный, расширение по мере надобности).
 *
 * Безопасная схема «remote API + кэш + embedded fallback»:
 * 1. in-memory кэш (TTL сутки);
 * 2. disk-кэш (`filesDir/remote_config.json`, любой возраст — если сеть недоступна);
 * 3. вшитый в APK дефолт — только если нет ни сети, ни кэша.
 *
 * Поведение приложения никогда не зависит от доступности сети: любой сбой сети/формата
 * молча откатывается к последнему известному значению.
 *
 * Endpoint: GitHub raw (изоляция от CF-досягаемости). Любая правка remote.json в main
 * распространяется за <24h на новых запусках. remote.json входит в репозиторий.
 */
data class RemoteBundle(
    val cfClientVersion: String = EMBEDDED_CF_CLIENT_VERSION,
    val telegramDcs: List<String> = EMBEDDED_TELEGRAM_DCS,
) {
    companion object {
        const val EMBEDDED_CF_CLIENT_VERSION = "a-6.30-3596"
        val EMBEDDED_TELEGRAM_DCS = listOf(
            "149.154.167.50",
            "149.154.167.51",
            "149.154.175.50",
            "91.108.56.130",
            "91.108.4.130",
            "91.108.8.130",
        )
        val EMBEDDED = RemoteBundle()
    }
}

class RemoteConfigStore(
    private val cacheFile: File,
    private val endpoint: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 24L * 60 * 60 * 1000,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile private var memory: Pair<RemoteBundle, Long>? = null // bundle -> fetchedAtMs

    suspend fun cfClientVersion(): String = withContext(Dispatchers.IO) { bundle().cfClientVersion }

    suspend fun telegramDcs(): List<String> = withContext(Dispatchers.IO) { bundle().telegramDcs }

    suspend fun bundle(): RemoteBundle {
        val now = clock()
        memory?.let { (b, t) -> if (now - t < ttlMs) return b }
        readCacheFresh(now)?.let { return it }
        val fetched = fetchRemote()
        if (fetched != null) {
            writeCache(fetched, now)
            memory = Pair(fetched, now)
            return fetched
        }
        return readCacheAnyAge() ?: RemoteBundle.EMBEDDED
    }

    internal fun parse(body: String): RemoteBundle? = runCatching {
        val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        val v = obj["cf_client_version"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (v.isBlank()) return null
        val dcs = obj["telegram_dcs"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.trim().takeIf { s -> s.isNotBlank() } }
            ?.filter { it.matches(IP_RX) }
            ?.takeIf { it.isNotEmpty() }
            ?: RemoteBundle.EMBEDDED_TELEGRAM_DCS
        RemoteBundle(cfClientVersion = v, telegramDcs = dcs)
    }.getOrNull()

    private fun fetchRemote(): RemoteBundle? = runCatching {
        httpClient.newCall(
            Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string()?.let(::parse)
        }
    }.getOrNull()

    private fun readCacheFresh(now: Long): RemoteBundle? =
        readCache()?.takeIf { (_, t) -> now - t < ttlMs }?.also { (b, t) -> memory = Pair(b, t) }?.first

    private fun readCacheAnyAge(): RemoteBundle? =
        readCache()?.also { (b, t) -> memory = Pair(b, t) }?.first

    /** Возвращает (bundle, fetchedAtMs) либо null. */
    private fun readCache(): Pair<RemoteBundle, Long>? = runCatching {
        if (!cacheFile.exists()) return@runCatching null
        val obj = Json { ignoreUnknownKeys = true }
            .parseToJsonElement(cacheFile.readText(Charsets.UTF_8)).jsonObject
        val t = obj["fetched_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
        if (t <= 0L) return@runCatching null
        val v = obj["cf_client_version"]?.jsonPrimitive?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: RemoteBundle.EMBEDDED_CF_CLIENT_VERSION
        val dcs = obj["telegram_dcs"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.trim().takeIf { s -> s.isNotBlank() } }
            ?.filter { it.matches(IP_RX) }
            ?.takeIf { it.isNotEmpty() }
            ?: RemoteBundle.EMBEDDED_TELEGRAM_DCS
        Pair(RemoteBundle(cfClientVersion = v, telegramDcs = dcs), t)
    }.getOrNull()

    private fun writeCache(bundle: RemoteBundle, fetchedAt: Long) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val escV = bundle.cfClientVersion.replace("\\", "\\\\").replace("\"", "\\\"")
            val escDcs = bundle.telegramDcs.joinToString(",", "[", "]") { "\"$it\"" }
            cacheFile.writeText(
                """{"cf_client_version":"$escV","telegram_dcs":$escDcs,"fetched_at":$fetchedAt}""",
                Charsets.UTF_8,
            )
        }
    }

    fun clearCache() {
        memory = null
        runCatching { cacheFile.delete() }
    }
}

/** App-level singleton (паттерн проекта: object + AppGraph.appContext). */
object RemoteConfig {
    const val ENDPOINT = "https://raw.githubusercontent.com/abonent0007/svoboda2/main/config/remote.json"

    @Volatile private var store: RemoteConfigStore? = null

    private fun store(): RemoteConfigStore =
        store ?: synchronized(this) {
            store ?: RemoteConfigStore(
                cacheFile = File(AppGraph.appContext.applicationContext.filesDir, "remote_config.json"),
                endpoint = ENDPOINT,
            ).also { store = it }
        }

    /** CF-Client-Version для api.cloudflareclient.com (remote → кэш → embedded). */
    suspend fun cfClientVersion(): String = store().cfClientVersion()

    /** Telegram DC список для probing (remote → кэш → embedded 149.154.167.50…). */
    suspend fun telegramDcs(): List<String> = store().telegramDcs()
}
