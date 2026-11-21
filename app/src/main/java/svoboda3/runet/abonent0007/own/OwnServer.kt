package svoboda3.runet.abonent0007.own

import kotlinx.serialization.Serializable
import svoboda3.runet.abonent0007.secure.SecureConfigStorage

/** Собственный AmneziaWG-сервер (локация + полный конфиг). */
@Serializable
data class OwnServer(
    val id: String = "",
    val countryName: String = "",
    val countryCode: String = "",
    val city: String = "",
    val endpointHost: String = "",
    val endpointPort: Int = 0,
    val config: String = "",
    val configEncrypted: String = "",
    val lastStatus: String = "",
    val lastCheckedAt: Long = 0L,
    val addedAt: Long = 0L,
    val bundled: Boolean = false,
) {
    /** Эмодзи-флаг из ISO-кода страны (или 🌐, если код неизвестен). */
    val flag: String
        get() {
            val code = countryCode.trim().uppercase()
            if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "\uD83C\uDF10"
            val base = 0x1F1E6 - 'A'.code
            return String(Character.toChars(base + code[0].code)) +
                String(Character.toChars(base + code[1].code))
        }

    /** «🇳🇱 Нидерланды · Amsterdam» — для списка и плитки. */
    val locationTitle: String
        get() {
            val head = listOf(flag, countryName.ifBlank { countryCode })
                .filter { it.isNotBlank() }
                .joinToString(" ")
            return if (city.isBlank()) head else "$head \u00B7 $city"
        }

    /** Расшифрованный конфиг (из открытого поля или из зашифрованного). */
    val resolvedConfig: String
        get() = if (config.isNotBlank()) config
        else SecureConfigStorage.decrypt(configEncrypted).orEmpty()
}

/** Извлекает Endpoint = host:port из AmneziaWG-конфига. */
object OwnServerParser {
    private val endpointRegex = Regex("(?mi)^\\s*Endpoint\\s*=\\s*(\\S+)\\s*$")

    fun endpoint(config: String): Pair<String, Int>? {
        val value = endpointRegex.find(config)?.groupValues?.get(1) ?: return null
        val host = value.substringBeforeLast(":", missingDelimiterValue = value)
        val port = value.substringAfterLast(":", missingDelimiterValue = "")
            .toIntOrNull() ?: 0
        return if (host.isBlank()) null else host to port
    }
}
