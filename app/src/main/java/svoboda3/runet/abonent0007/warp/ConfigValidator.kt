package svoboda3.runet.abonent0007.warp

import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Результат валидации AmneziaWG/WireGuard-конфига.
 * Ошибки блокируют применение; предупреждения — информируют.
 */
data class ValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Валидация конфига до применения: синтаксис, обязательные поля,
 * формат ключей/IP/Endpoint, диапазоны AWG-параметров, дубликаты AllowedIPs,
 * наличие DNS/IPv6-маршрута.
 */
object ConfigValidator {

    private val b64Key = Regex("^[A-Za-z0-9+/]{42,44}={0,2}$")
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val cidr = Regex("^\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}$")

    fun validate(text: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            errors += "Конфиг пуст"
            return ValidationResult(errors, warnings)
        }
        if (!trimmed.contains("[Interface]", ignoreCase = true)) errors += "Отсутствует секция [Interface]"
        if (!trimmed.contains("[Peer]", ignoreCase = true)) errors += "Отсутствует секция [Peer]"

        // Парсинг через движок: ловит синтаксические ошибки.
        val parsed = runCatching {
            Config.parse(BufferedReader(StringReader(trimmed)))
        }
        if (parsed.isFailure) {
            errors += "Синтаксическая ошибка: ${parsed.exceptionOrNull()?.message ?: "неизвестно"}"
            return ValidationResult(errors, warnings)
        }
        val cfg = parsed.getOrThrow()
        val iface = cfg.`interface`

        // Ключи.
        if (iface.keyPair.privateKey.toBase64().isBlank()) errors += "Отсутствует PrivateKey"
        else if (!b64Key.matches(iface.keyPair.privateKey.toBase64())) warnings += "PrivateKey выглядит некорректно"

        cfg.peers.forEachIndexed { idx, peer ->
            if (peer.publicKey.toBase64().isBlank()) errors += "Peer #${idx + 1}: отсутствует PublicKey"
            if (peer.endpoint == null) warnings += "Peer #${idx + 1}: отсутствует Endpoint"
            if (peer.allowedIps.isEmpty()) warnings += "Peer #${idx + 1}: пустой AllowedIPs"
        }
        if (cfg.peers.isEmpty()) errors += "Отсутствует секция [Peer] с параметрами"

        // Адреса.
        if (iface.addresses.isEmpty()) errors += "Отсутствует Address в [Interface]"
        iface.addresses.forEach { addr ->
            val host = addr.address.hostAddress.orEmpty()
            if (!ipv4.matches(host) && !host.contains(":")) {
                warnings += "Некорректный адрес: $addr"
            }
        }

        // DNS.
        if (iface.dnsServers.isEmpty()) warnings += "Не указаны DNS-серверы"
        // MTU.
        val mtu = iface.mtu.orElse(0)
        if (mtu > 0 && (mtu < 1200 || mtu > 1500)) warnings += "MTU $mtu вне рекомендуемого диапазона 1200–1500"

        // AWG-параметры (если присутствуют) — проверка диапазонов.
        runCatching {
            val jc = iface.junkPacketCount.orElse(0)
            if (jc < 0 || jc > 128) warnings += "Jc=$jc вне диапазона 0–128"
        }
        // Duplicate AllowedIPs.
        val allAllowed = cfg.peers.flatMap { it.allowedIps.map { it.toString() } }
        if (allAllowed.size != allAllowed.distinct().size) warnings += "Дублирующиеся AllowedIPs"

        // IPv6 route check.
        val hasV6 = allAllowed.any { it.contains(":") } || iface.addresses.any { it.address.hostAddress?.contains(":") == true }
        if (!hasV6) warnings += "IPv6-маршрут отсутствует: туннель будет IPv4-only"

        // Endpoint host resolution sanity.
        cfg.peers.forEach { peer ->
            peer.endpoint.orElse(null)?.host?.let { host ->
                if (host.isBlank()) warnings += "Пустой hostname в Endpoint"
            }
        }

        return ValidationResult(errors, warnings)
    }
}
