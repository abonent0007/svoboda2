package svoboda3.runet.abonent0007.dpi.benchmark

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Минимальный SOCKS5-клиент для измерений через ciadpi.
 *
 * Важно: SOCKS5-сервер на 127.0.0.1:<port> — это сам ciadpi, поэтому любой
 * запрос через него гарантированно проходит через проверяемую стратегию.
 * Прямой (не-SOCKS) трафик приложения идёт мимо туннеля и для измерений непригоден.
 */
class Socks5Client(
    private val host: String = "127.0.0.1",
    private val port: Int = 1080,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) {

    class SocksError(val code: Int, message: String) : IOException(message)

    /** Результат успешного CONNECT. */
    data class Connected(
        val socket: Socket,
        val boundAddress: String,
        val elapsedMs: Long,
    )

    private fun openSocket(): Socket {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = readTimeoutMs
        s.tcpNoDelay = true
        return s
    }

    private fun handshake(socket: Socket) {
        val out = socket.outputStream
        out.write(0x05)
        out.write(0x01)
        out.write(0x00)
        out.flush()
        val resp = ByteArray(2)
        var read = 0
        while (read < 2) {
            val n = socket.inputStream.read(resp, read, 2 - read)
            if (n < 0) throw SocksError(0, "SOCKS: нет ответа на приветствие")
            read += n
        }
        if (resp[0] != 0x05.toByte()) throw SocksError(0, "SOCKS: неверная версия ${resp[0]}")
        if (resp[1] != 0x00.toByte()) throw SocksError(0, "SOCKS: требуется аутентификация")
    }

    private fun request(socket: Socket, atype: Byte, address: ByteArray, hostName: String?, targetPort: Int) {
        val out = socket.outputStream
        out.write(0x05)
        out.write(0x01)
        out.write(0x00)
        out.write(atype.toInt())
        if (hostName != null) {
            val hb = hostName.toByteArray(Charsets.UTF_8)
            out.write(hb.size)
            out.write(hb)
        } else {
            out.write(address)
        }
        out.write((targetPort shr 8) and 0xFF)
        out.write(targetPort and 0xFF)
        out.flush()
    }

    private fun readReply(socket: Socket): Pair<Byte, String> {
        val input = socket.inputStream
        val head = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(head, read, 4 - read)
            if (n < 0) throw SocksError(0, "SOCKS: обрыв при чтении ответа")
            read += n
        }
        val rep = head[1]
        val atyp = head[3]
        val bound = when (atyp) {
            0x01.toByte() -> {
                val b = ByteArray(4); readFully(input, b); b.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }
            0x03.toByte() -> {
                val len = readFully(input, ByteArray(1))[0].toInt() and 0xFF
                String(readFully(input, ByteArray(len)), Charsets.UTF_8)
            }
            0x04.toByte() -> {
                val b = readFully(input, ByteArray(16))
                b.joinToString(":") { (it.toInt() and 0xFF).toString() }
            }
            else -> "?"
        }
        readFully(input, ByteArray(2))
        return rep to bound
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): ByteArray {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) throw SocksError(0, "SOCKS: неполный ответ")
            read += n
        }
        return buffer
    }

    /** CONNECT по домену: резолвит ciadpi — возвращает bound-address (IP, который он получил). */
    fun connectDomain(hostName: String, targetPort: Int): Connected {
        val started = System.nanoTime()
        val socket = openSocket()
        try {
            handshake(socket)
            request(socket, 0x03, ByteArray(0), hostName, targetPort)
            val (rep, bound) = readReply(socket)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            if (rep != 0.toByte()) {
                socket.close()
                throw SocksError(rep.toInt() and 0xFF, socksMessage(rep.toInt() and 0xFF))
            }
            return Connected(socket, bound, elapsed)
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw e
        }
    }

    /** CONNECT по IP-литералу: чистый TCP-шаг без повторного резолва. */
    fun connectIp(ip: String, targetPort: Int): Connected {
        val started = System.nanoTime()
        val socket = openSocket()
        try {
            handshake(socket)
            val parts = ip.split('.')
            val address = if (parts.size == 4) {
                ByteArray(4) { parts[it].toInt().toByte() }
            } else {
                java.net.InetAddress.getByName(ip).address
            }
            request(socket, 0x01, address, null, targetPort)
            val (rep, bound) = readReply(socket)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            if (rep != 0.toByte()) {
                socket.close()
                throw SocksError(rep.toInt() and 0xFF, socksMessage(rep.toInt() and 0xFF))
            }
            return Connected(socket, bound, elapsed)
        } catch (e: Throwable) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun socksMessage(code: Int): String = when (code) {
        1 -> "SOCKS: общий отказ"
        2 -> "SOCKS: правило запрещено"
        3 -> "SOCKS: сеть недоступна"
        4 -> "SOCKS: хост недоступен (DNS/маршрут)"
        5 -> "SOCKS: соединение отклонено"
        6 -> "SOCKS: TTL истёк"
        7 -> "SOCKS: команда не поддерживается"
        8 -> "SOCKS: тип адреса не поддерживается"
        else -> "SOCKS: код $code"
    }
}
