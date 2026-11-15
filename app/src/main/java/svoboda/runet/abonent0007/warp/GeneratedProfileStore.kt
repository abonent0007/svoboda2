package svoboda.runet.abonent0007.warp

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import svoboda.runet.abonent0007.secure.SecureConfigStorage
import java.io.File

class GeneratedProfileStore(context: Context) {

    companion object {
        private const val FILE_NAME = "generated_profiles.json"
        private const val MAX_ENTRIES = 30
    }

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun list(): List<GeneratedProfile> {
        val raw = runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString<List<GeneratedProfile>>(file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyList<GeneratedProfile>())

        var changed = false
        val migrated = raw.map { p ->
            if (p.configEncrypted.isNotBlank()) {
                p.copy(config = SecureConfigStorage.decrypt(p.configEncrypted) ?: p.config)
            } else if (p.config.isNotBlank()) {
                val enc = SecureConfigStorage.encrypt(p.config)
                if (enc != null) {
                    changed = true
                    p.copy(configEncrypted = enc, config = p.config)
                } else {
                    p
                }
            } else {
                p
            }
        }
        if (changed) saveEncrypted(migrated)
        return migrated
    }

    fun add(profile: GeneratedProfile) {
        runCatching {
            val enc = SecureConfigStorage.encrypt(profile.config) ?: profile.configEncrypted
            val withEnc = profile.copy(configEncrypted = enc, config = profile.config)
            val updated = (list() + withEnc).takeLast(MAX_ENTRIES)
            saveEncrypted(updated)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun saveEncrypted(items: List<GeneratedProfile>) {
        val toStore: List<GeneratedProfile> = items.map { profile: GeneratedProfile -> profile.copy(config = "") }
        runCatching { file.writeText(json.encodeToString(toStore), Charsets.UTF_8) }
    }
}
