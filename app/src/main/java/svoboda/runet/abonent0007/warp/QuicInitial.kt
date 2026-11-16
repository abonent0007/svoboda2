package svoboda.runet.abonent0007.warp

import java.security.SecureRandom

/**
 * Собственная реализация «магического» пакета для AmneziaWG I1:
 * пакет, который выглядит как QUIC Initial (RFC 9000) — такой формат
 * распознают DPI-фильтры, обучающиеся на QUIC/маскировке.
 *
 * Идея (общая у публичных Mini QUIC Generator): AmneziaWG 1.5+ умеет подставлять
 * в начало исходящего пакета заранее заданные байты (I1), и если эти байты —
 * корректный QUIC Initial, фильтр не видит там WireGuard-handshake.
 *
 * Здесь пакет собирается нами: long header QUIC v1 + CRYPTO-фрейм с правдоподобным
 * TLS ClientHello. Точная байтовая копия чужих генераторов не воспроизводится —
 * своя реализация по спецификации.
 */
object QuicInitial {

    private val random = SecureRandom()

    /** Собрать QUIC Initial-пакет с фейковым TLS ClientHello внутри CRYPTO-фрейма. */
    fun build(clientHelloRandom: ByteArray = randomBytes(32)): ByteArray {
        val dcid = randomBytes(8)
        val scid = randomBytes(8)
        val tls = fakeClientHello(clientHelloRandom)

        // CRYPTO frame: type 0x06 + offset (varint 0) + length (varint) + data
        val cryptoFrame = ByteArrayOutputStreamLite().apply {
            writeByte(0x06)
            writeVarInt(0)          // offset
            writeVarInt(tls.size.toLong())
            write(tls)
        }.toByteArray()

        // Payload length = packet number (4) + crypto frame
        val payload = ByteArrayOutputStreamLite().apply {
            write(intToBytes(1))   // packet number (4 bytes, random-ish)
            write(cryptoFrame)
        }.toByteArray()

        val out = ByteArrayOutputStreamLite().apply {
            writeByte(0xC3)        // long header, fixed bit, PN length 4
            write(intToBytes(1))   // version QUIC v1
            writeByte(dcid.size)
            write(dcid)
            writeByte(scid.size)
            write(scid)
            writeVarInt(0)          // token length
            writeVarInt(payload.size.toLong())
            write(payload)
        }
        return out.toByteArray()
    }

    /** Строковое представление для I1: формат AmneziaWG `<b 0x...>`. */
    fun toMagicHex(packet: ByteArray): String {
        val hex = packet.joinToString("") { "%02x".format(it) }
        return "<b 0x$hex>"
    }

    /** Правдоподобный TLS ClientHello (record + handshake, без SNI). */
    private fun fakeClientHello(clientRandom: ByteArray): ByteArray {
        val body = ByteArrayOutputStreamLite().apply {
            writeByte(0x01)                       // ClientHello
            write(intToBytes(0x0303))             // legacy_version TLS 1.2
            write(clientRandom)                   // random (32)
            writeByte(0x00)                       // session id length
            write(byteArrayOf(0x00, 0x2F))        // cipher suites (TLS 1.3 + AES-GCM)
            write(byteArrayOf(0x01, 0x00))        // compression methods
            write(byteArrayOf(0x00, 0x00))        // extensions length
        }.toByteArray()

        val handshake = ByteArrayOutputStreamLite().apply {
            writeByte(0x01)
            writeByte((body.size shr 16) and 0xFF)
            writeByte((body.size shr 8) and 0xFF)
            writeByte(body.size and 0xFF)
            write(body)
        }.toByteArray()

        return ByteArrayOutputStreamLite().apply {
            writeByte(0x16)                       // TLS handshake record
            writeByte(0x03)
            writeByte(0x01)                       // TLS 1.0 record version
            write(intToBytes(handshake.size, 2))  // record length (2 bytes)
            write(handshake)
        }.toByteArray()
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    private fun intToBytes(value: Int, len: Int = 4): ByteArray {
        val out = ByteArray(len)
        for (i in 0 until len) {
            out[len - 1 - i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    /** Минимальный буфер для сборки байтов (свой, чтобы не тянуть java.io на каждый байт). */
    private class ByteArrayOutputStreamLite {
        private var buf = ByteArray(256)
        private var size = 0

        fun writeByte(b: Int) {
            ensure(1)
            buf[size++] = b.toByte()
        }

        fun write(bytes: ByteArray) {
            ensure(bytes.size)
            System.arraycopy(bytes, 0, buf, size, bytes.size)
            size += bytes.size
        }

        fun writeVarInt(value: Long) {
            if (value < 64) {
                writeByte(value.toInt())
            } else if (value < 16384) {
                writeByte(0x40 or ((value shr 6).toInt() and 0x3F))
                writeByte((value and 0x3F).toInt())
            } else {
                writeByte(0x80 or ((value shr 14).toInt() and 0x3F))
                writeByte(((value shr 6).toInt() and 0x3F))
                writeByte((value and 0x3F).toInt())
            }
        }

        private fun ensure(extra: Int) {
            if (size + extra <= buf.size) return
            var newSize = buf.size * 2
            while (newSize < size + extra) newSize *= 2
            buf = buf.copyOf(newSize)
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
