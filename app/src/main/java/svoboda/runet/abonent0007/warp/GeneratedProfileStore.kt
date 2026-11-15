package svoboda.runet.abonent0007.warp

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Один сгенерированный вручную WARP-профиль. */
@Serializable
data class GeneratedProfile(
    val createdAt: Long = 0L,
    val host: String = "",
    val port: Int = 0,
    val config: String = "",
)

/**
 * Список сгенерированных профилей (файл в filesDir).
 *
 * Порядок — от самого старого к новому. Откат после неудачи идёт
 * с НАЧАЛА списка (первый сгенерированный профиль), как согласовано.
 */
class GeneratedProfileStore(context: Context) {

    companion object {
        private const val FILE_NAME = "generated_profiles.json"
        private const val MAX_ENTRIES = 30
    }

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun list(): List<GeneratedProfile> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<GeneratedProfile>>(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    fun add(profile: GeneratedProfile) {
        runCatching {
            val updated = (list() + profile).takeLast(MAX_ENTRIES)
            file.writeText(json.encodeToString(updated), Charsets.UTF_8)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
