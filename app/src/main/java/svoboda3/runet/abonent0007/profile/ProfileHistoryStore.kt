package svoboda3.runet.abonent0007.profile

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import svoboda3.runet.abonent0007.secure.SecureConfigStorage
import java.io.File
import java.util.UUID

@Serializable
data class ProfileRevision(
    val revisionId: String = "",
    val profileId: String = "",
    val version: Int = 0,
    val createdAt: Long = 0L,
    val changeType: String = "",
    val fingerprint: String = "",
    val encryptedConfig: String = "",
    val changeSummary: String = "",
)

class ProfileHistoryStore(context: Context) {

    companion object {
        private const val FILE_NAME = "profile_history.json"
        private const val MAX_REVISIONS = 10
    }

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun listRevisions(profileId: String): List<ProfileRevision> {
        return runCatching {
            if (!file.exists()) emptyList()
            else json.decodeFromString<List<ProfileRevision>>(file.readText(Charsets.UTF_8))
                .filter { it.profileId == profileId }
                .sortedBy { it.version }
        }.getOrDefault(emptyList())
    }

    fun addRevision(profileId: String, config: String, changeType: String, summary: String, fingerprint: String) {
        runCatching {
            val all = if (file.exists()) json.decodeFromString<MutableList<ProfileRevision>>(file.readText(Charsets.UTF_8)) else mutableListOf()
            val currentCount = all.count { it.profileId == profileId }
            val rev = ProfileRevision(
                revisionId = UUID.randomUUID().toString(),
                profileId = profileId,
                version = currentCount + 1,
                createdAt = System.currentTimeMillis(),
                changeType = changeType,
                fingerprint = fingerprint,
                encryptedConfig = SecureConfigStorage.encrypt(config).orEmpty(),
                changeSummary = summary,
            )
            val updated = (all + rev).let { list ->
                // Keep last MAX_REVISIONS per profile
                val grouped = list.groupBy { it.profileId }
                grouped.flatMap { (_, revs) -> revs.sortedBy { it.version }.takeLast(MAX_REVISIONS) }
            }
            file.writeText(json.encodeToString(updated), Charsets.UTF_8)
        }
    }

    fun restore(profileId: String, revisionId: String): String? {
        val rev = listRevisions(profileId).firstOrNull { it.revisionId == revisionId } ?: return null
        val config = SecureConfigStorage.decrypt(rev.encryptedConfig) ?: return null
        // Create a new revision for the restore
        addRevision(profileId, config, "RESTORE", "Восстановлено из версии ${rev.version}", rev.fingerprint)
        return config
    }

    fun deleteHistory(profileId: String) {
        runCatching {
            if (!file.exists()) return
            val all = json.decodeFromString<MutableList<ProfileRevision>>(file.readText(Charsets.UTF_8))
            val filtered = all.filterNot { it.profileId == profileId }
            file.writeText(json.encodeToString(filtered), Charsets.UTF_8)
        }
    }
}
