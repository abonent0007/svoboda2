package svoboda.runet.abonent0007.split

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SplitTunnelStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "split_tunnel.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun get(profileId: String): SplitTunnelConfig? {
        if (profileId.isBlank()) return null
        return runCatching {
            if (!file.exists()) null
            else {
                val map = json.decodeFromString<Map<String, SplitTunnelConfig>>(file.readText(Charsets.UTF_8))
                map[profileId]
            }
        }.getOrNull()
    }

    fun set(config: SplitTunnelConfig) {
        if (config.profileId.isBlank()) return
        runCatching {
            val map = if (file.exists()) json.decodeFromString<MutableMap<String, SplitTunnelConfig>>(file.readText(Charsets.UTF_8)) else mutableMapOf()
            map[config.profileId] = config.copy(updatedAt = System.currentTimeMillis())
            file.writeText(json.encodeToString(map), Charsets.UTF_8)
        }
    }

    fun remove(profileId: String) {
        runCatching {
            if (!file.exists()) return
            val map = json.decodeFromString<MutableMap<String, SplitTunnelConfig>>(file.readText(Charsets.UTF_8))
            map.remove(profileId)
            file.writeText(json.encodeToString(map), Charsets.UTF_8)
        }
    }

    fun all(): Map<String, SplitTunnelConfig> = runCatching {
        if (!file.exists()) emptyMap()
        else json.decodeFromString<Map<String, SplitTunnelConfig>>(file.readText(Charsets.UTF_8))
    }.getOrDefault(emptyMap())
}
