package svoboda3.runet.abonent0007.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Каталог DNS-резолверов с проверкой валидности.
 *
 * Проверка — реальный DNS-запрос (A-запись) к резолверу с таймаутом:
 * резолвер считается рабочим, если он ответил и вернул адреса.
 * Для доменных резолверов сначала резолвится сам домен (системным DNS).
 *
 * IP-адреса антиблок-резолверов (malw/geohide/comss/mafioznik) — публичные данные
 * из проекта nellimonix/warp-config-generator-vercel (MIT License, © 2025 nellimonix).
 */
object DnsResolvers {

    data class Def(
        val id: String,
        val title: String,
        /** IP-адрес или домен. */
        val host: String,
        /** Известные адреса (для доменных резолверов). */
        val knownIps: List<String> = emptyList(),
        /** Антиблок-резолверы возвращают адреса «для обхода» — им нельзя частичный трафик. */
        val community: Boolean = false,
        val note: String? = null,
    )

    data class Check(
        val ok: Boolean,
        val ips: List<String>,
        val latencyMs: Long?,
        val error: String?,
    )

    val catalog: List<Def> = listOf(
        Def("cf", "Cloudflare", "1.1.1.1", listOf("1.1.1.1", "1.0.0.1"), false, "1.1.1.1"),
        Def("google", "Google", "8.8.8.8", listOf("8.8.8.8", "8.8.4.4"), false, "8.8.8.8"),
        Def("quad9", "Quad9", "dns.quad9.net", listOf("9.9.9.9", "149.112.112.112"), false, "Блокировка фишинга"),
        Def("malw", "dns.malw.link", "dns.malw.link", listOf("95.216.204.218", "80.253.249.40"), true, "Антиблок"),
        Def("bezmezhau", "bezmezhau.com", "bezmezhau.com", emptyList(), true, "Антиблок"),
        Def("geohide", "geohide.ru", "geohide.ru", listOf("45.155.204.190", "37.230.192.51", "159.194.200.33", "193.233.112.67", "193.233.112.88"), true, "Антиблок"),
        Def("geodema", "dns.geodema.love", "dns.geodema.love", emptyList(), true, "Антиблок"),
        Def("mafioznik", "dns.mafioznik.xyz", "dns.mafioznik.xyz", listOf("103.27.157.38", "103.27.157.100"), true, "Антиблок"),
        Def("comss", "dns.comss.one", "dns.comss.one", listOf("83.220.169.155", "212.109.195.93", "195.133.25.16"), true, "Антиблок"),
    )

    fun byId(id: String): Def? = catalog.firstOrNull { it.id == id }

    /** Адреса резолвера: известные или разрешённые сейчас. */
    suspend fun addressesOf(def: Def): List<String> = withContext(Dispatchers.IO) {
        if (def.knownIps.isNotEmpty()) return@withContext def.knownIps
        runCatching {
            InetAddress.getAllByName(def.host).map { it.hostAddress }.distinct()
        }.getOrDefault(emptyList())
    }

    /** Адреса произвольного (пользовательского) резолвера: IP или домен. */
    suspend fun addressesOfCustom(value: String): List<String> = withContext(Dispatchers.IO) {
        val v = value.trim()
        if (v.isEmpty()) return@withContext emptyList()
        if (v.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || v.contains(':')) {
            return@withContext listOf(v)
        }
        runCatching {
            InetAddress.getAllByName(v).map { it.hostAddress }.distinct()
        }.getOrDefault(emptyList())
    }

    /** Проверка резолвера: реальный DNS-запрос. */
    suspend fun check(def: Def): Check = checkAddresses(addressesOf(def))

    /** Проверка произвольного значения. */
    suspend fun checkCustom(value: String): Check = checkAddresses(addressesOfCustom(value))

    private suspend fun checkAddresses(addresses: List<String>): Check = withContext(Dispatchers.IO) {
        if (addresses.isEmpty()) {
            return@withContext Check(false, emptyList(), null, "не удалось разрешить адрес")
        }
        var lastError: String? = null
        var bestLatency: Long? = null
        for (address in addresses) {
            val started = System.nanoTime()
            val result = queryA(address, "example.com", 3_000)
            if (result) {
                val latency = (System.nanoTime() - started) / 1_000_000
                if (bestLatency == null || latency < bestLatency) bestLatency = latency
            } else {
                lastError = "нет ответа от $address"
            }
        }
        Check(bestLatency != null, addresses, bestLatency, lastError)
    }

    /** Простой DNS A-запрос по UDP. */
    private fun queryA(server: String, name: String, timeoutMs: Int): Boolean {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val packet = buildQuery(name)
                socket.send(DatagramPacket(packet, packet.size, InetSocketAddress(server, 53)))
                val buffer = ByteArray(512)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                if (reply.length < 12) return false
                val flags = ((buffer[2].toInt() and 0xFF) shl 8) or (buffer[3].toInt() and 0xFF)
                val rcode = flags and 0x000F
                val answers = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)
                rcode == 0 && answers > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildQuery(name: String): ByteArray {
        val id = (System.nanoTime() and 0xFFFF).toInt()
        val out = java.io.ByteArrayOutputStream()
        out.write(id shr 8)
        out.write(id and 0xFF)
        out.write(0x01)   // RD
        out.write(0x00)
        out.write(0x00); out.write(0x01)   // QDCOUNT
        out.write(0x00); out.write(0x00)   // ANCOUNT
        out.write(0x00); out.write(0x00)   // NSCOUNT
        out.write(0x00); out.write(0x00)   // ARCOUNT
        name.split('.').filter { it.isNotEmpty() }.forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.UTF_8))
        }
        out.write(0x00)
        out.write(0x00); out.write(0x01)   // QTYPE A
        out.write(0x00); out.write(0x01)   // QCLASS IN
        return out.toByteArray()
    }
}
