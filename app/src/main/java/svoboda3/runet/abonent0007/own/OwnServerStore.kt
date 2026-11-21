package svoboda3.runet.abonent0007.own

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import svoboda3.runet.abonent0007.secure.SecureConfigStorage
import java.io.File
import java.util.UUID

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
            }.getOrDefault(emptyList<OwnServer>())
            val toSave = seeded.map { it.copy(config = "", configEncrypted = SecureConfigStorage.encrypt(it.config) ?: it.configEncrypted) }
            saveEncrypted(toSave.ifEmpty { emptyList() })
            if (seeded.isEmpty()) saveEncrypted(emptyList())
        }
    }

    fun list(): List<OwnServer> {
        val raw = runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString<List<OwnServer>>(file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList<OwnServer>())

        // Миграция: открытый config -> зашифрованный.
        var changed = false
        val migrated = raw.map { s ->
            if (s.configEncrypted.isNotBlank()) {
                s.copy(config = SecureConfigStorage.decrypt(s.configEncrypted) ?: s.config)
            } else if (s.config.isNotBlank()) {
                val enc = SecureConfigStorage.encrypt(s.config)
                if (enc != null) {
                    changed = true
                    s.copy(configEncrypted = enc, config = s.config)
                } else {
                    s
                }
            } else {
                s
            }
        }
        if (changed) saveEncrypted(migrated)
        return migrated
    }

    fun add(server: OwnServer): OwnServer {
        val enc = SecureConfigStorage.encrypt(server.config) ?: server.configEncrypted
        val withId = server.copy(
            id = server.id.ifBlank { UUID.randomUUID().toString() },
            addedAt = server.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            configEncrypted = enc,
            config = server.config,
        )
        val updated = list() + withId
        saveEncrypted(updated)
        return withId
    }

    fun update(server: OwnServer) {
        val enc = if (server.config.isNotBlank()) {
            SecureConfigStorage.encrypt(server.config) ?: server.configEncrypted
        } else {
            server.configEncrypted
        }
        val updated = server.copy(configEncrypted = enc, config = server.config)
        saveEncrypted(list().map { if (it.id == server.id) updated else it })
    }

    fun remove(id: String) {
        saveEncrypted(list().filterNot { it.id == id })
    }

    fun find(id: String): OwnServer? = list().firstOrNull { it.id == id }

    private fun saveEncrypted(items: List<OwnServer>) {
        val toStore = items.map { it.copy(config = "") }
        runCatching { file.writeText(json.encodeToString(toStore), Charsets.UTF_8) }
    }
}
