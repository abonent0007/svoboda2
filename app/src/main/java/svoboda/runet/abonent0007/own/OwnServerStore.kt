package svoboda.runet.abonent0007.own

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Хранилище собственных AmneziaWG-серверов (файл в filesDir).
 * Список — в порядке добавления; активный сервер хранится в Prefs.
 */
class OwnServerStore(context: Context) {

    companion object {
        private const val FILE_NAME = "own_servers.json"
        private const val SEED_ASSET = "seeds/own_servers_seed.json"
    }

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        if (!file.exists()) {
            val seeded = runCatching {
                val text = appContext.assets.open(SEED_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
                json.decodeFromString<List<OwnServer>>(text)
            }.getOrDefault(emptyList())
            if (seeded.isNotEmpty()) {
                save(seeded)
            } else {
                // Пустой файл — чтобы не пытаться пересаживать при каждом запуске.
                save(emptyList())
            }
        }
    }

    fun list(): List<OwnServer> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<OwnServer>>(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun add(server: OwnServer): OwnServer {
        val withId = server.copy(
            id = server.id.ifBlank { UUID.randomUUID().toString() },
            addedAt = server.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        save(list() + withId)
        return withId
    }

    fun update(server: OwnServer) {
        save(list().map { if (it.id == server.id) server else it })
    }

    fun remove(id: String) {
        save(list().filterNot { it.id == id })
    }

    fun find(id: String): OwnServer? = list().firstOrNull { it.id == id }

    private fun save(items: List<OwnServer>) {
        runCatching { file.writeText(json.encodeToString(items), Charsets.UTF_8) }
    }
}
