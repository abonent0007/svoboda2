package svoboda.runet.abonent0007.warp

/**
 * Нормализация AmneziaWG-конфигов перед парсингом.
 *
 * 1. Seed-конфиги содержат `Address = 172.16.0.2, 2606:...` без масок —
 *    Go-бэкенд и VpnService.Builder требуют явную длину префикса.
 * 2. По умолчанию работаем в IPv4-only: на ряде устройств netd отклоняет
 *    установку IPv6-адреса на TUN («Cannot set address»). IPv6 включается
 *    отдельной настройкой позже.
 */
object ConfigNormalizer {

    private val addressLine = Regex("(?m)^\\s*Address\\s*=\\s*(.+)\\s*$")
    private val allowedIpsLine = Regex("(?m)^\\s*AllowedIPs\\s*=\\s*(.+)\\s*$")
    private val dnsLine = Regex("(?m)^\\s*DNS\\s*=\\s*(.+)\\s*$")
    private val mtuLine = Regex("(?m)^\\s*MTU\\s*=\\s*\\d+\\s*$")

    fun normalize(text: String, ipv6Enabled: Boolean = false, mtuOverride: Int? = null): String {
        val withMtu = text.lines().joinToString("\n") { line ->
            if (mtuOverride == null || !mtuLine.matches(line)) line else "MTU = $mtuOverride"
        }
        val normalizedAddresses = withMtu.lines().joinToString("\n") { line ->
            val match = addressLine.matchEntire(line)
            if (match == null) {
                line
            } else {
                val entries = match.groupValues[1]
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .filter { entry ->
                        ipv6Enabled || !entry.substringBefore("/").contains(":")
                    }
                    .map { address ->
                        when {
                            address.contains("/") -> address
                            address.contains(":") -> "$address/128"
                            else -> "$address/32"
                        }
                    }
                if (entries.isEmpty()) line else "Address = ${entries.joinToString(", ")}"
            }
        }
        return normalizedAddresses.lines().joinToString("\n") { line ->
            val ipMatch = allowedIpsLine.matchEntire(line)
            val dnsMatch = dnsLine.matchEntire(line)
            when {
                ipMatch != null -> {
                    val entries = ipMatch.groupValues[1]
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .filter { ipv6Enabled || !it.contains(":") }
                    if (entries.isEmpty()) line else "AllowedIPs = ${entries.joinToString(", ")}"
                }
                dnsMatch != null -> {
                    val entries = dnsMatch.groupValues[1]
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .filter { ipv6Enabled || !it.contains(":") }
                    if (entries.isEmpty()) line else "DNS = ${entries.joinToString(", ")}"
                }
                else -> line
            }
        }
    }
}
