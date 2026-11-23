package svoboda.runet.abonent0007.awg

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QUIC-маска для AWG 2.0 (предустановка v2) — порт клиентского генератора quic.js.
 *
 * Собирает QUIC Initial с TLS ClientHello (только SNI) и отдаёт AmneziaWG-совместимую
 * I1-строку вида `<b 0x…>` — её подставляет генератор в [AwgGenerator].
 */
object AwgQuicMask {

    private val SALT = byteArrayOf(
        0x38, 0x76, 0x2c, 0xf7.toByte(), 0xf5.toByte(), 0x59, 0x34, 0xb3.toByte(), 0x4d, 0x17,
        0x9a.toByte(), 0xe6.toByte(), 0xa4.toByte(), 0xc8.toByte(), 0x0c, 0xad.toByte(), 0xcc.toByte(),
        0xbb.toByte(), 0x7f, 0x0a,
    )

    private val random = SecureRandom()

    /** AmneziaWG I1 для заданного SNI. */
    fun generate(sni: String, level: Int = 0): String {
        val dcid = ByteArray(1).also { random.nextBytes(it) }
        val scid = ByteArray(0)
        val token = ByteArray(0)
        val pkn = byteArrayOf(0)
        val clientHello = tlsClientHelloSniOnly(sni)
        val (payload, cutSettings) = tlsClientHelloToFrames(clientHello, level)
        val packet = quicInitial(dcid, scid, token, pkn, payload, padTo = 0)
        fixCutSettings(cutSettings, packet.size, pkn.size, payload.size)
        return toAwg(packet, cutSettings)
    }

    // --- криптография ---

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun deriveSecret(key: ByteArray, length: Int, label: String, context: ByteArray = ByteArray(0)): ByteArray {
        val data = concat(putUint16(length), str8("tls13 $label"), str8Bytes(context), byteArrayOf(0x01))
        return hmac(key, data).copyOfRange(0, length)
    }

    private fun aesGcm(key: ByteArray, iv: ByteArray, aad: ByteArray, payload: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return c.doFinal(payload)
    }

    /** Один блок AES-CBC с нулевым IV — маска заголовка (header protection). */
    private fun aesCbcBlock(key: ByteArray, sample: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/CBC/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return c.doFinal(sample)
    }

    // --- кодирование ---

    private fun putUint16(v: Int): ByteArray = byteArrayOf(((v shr 8) and 0xff).toByte(), (v and 0xff).toByte())

    private fun str8(s: String): ByteArray = str8Bytes(s.toByteArray(Charsets.UTF_8))

    private fun str8Bytes(b: ByteArray): ByteArray =
        if (b.isEmpty()) byteArrayOf(0) else byteArrayOf(b.size.toByte()) + b

    private fun str16(s: String): ByteArray = str16Bytes(s.toByteArray(Charsets.UTF_8))

    private fun str16Bytes(b: ByteArray): ByteArray =
        byteArrayOf(((b.size shr 8) and 0xff).toByte(), (b.size and 0xff).toByte()) + b

    private fun varint(x: Int): ByteArray = when {
        x < 0x40 -> byteArrayOf(x.toByte())
        x < 0x4000 -> byteArrayOf((((x shr 8) and 0xff) or 0x40).toByte(), (x and 0xff).toByte())
        x < 0x40000000 -> byteArrayOf(
            (((x shr 24) and 0xff) or 0x80).toByte(), ((x shr 16) and 0xff).toByte(),
            ((x shr 8) and 0xff).toByte(), (x and 0xff).toByte(),
        )
        else -> throw IllegalArgumentException("varint too big")
    }

    private fun varintLength(x: Int): Int = when {
        x < 0x40 -> 1
        x < 0x4000 -> 2
        x < 0x40000000 -> 4
        else -> 8
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (p in parts) { p.copyInto(out, off); off += p.size }
        return out
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    // --- TLS ClientHello ---

    private fun tlsExtSni(sni: String): ByteArray {
        val sniB = str16(sni)
        val extBody = concat(putUint16(sniB.size + 1), byteArrayOf(0), sniB)
        return concat(byteArrayOf(0x00, 0x00), putUint16(extBody.size), extBody)
    }

    private fun tlsClientHelloSniOnly(sni: String): ByteArray {
        val random = ByteArray(32).also { random.nextBytes(it) }
        val body = concat(
            byteArrayOf(0x03, 0x03), random,
            byteArrayOf(0x00, 0x00, 0x00, 0x00),
            str16Bytes(tlsExtSni(sni)),
        )
        return concat(byteArrayOf(0x01), putUint24(body.size), body)
    }

    private fun putUint24(v: Int): ByteArray =
        byteArrayOf(((v shr 16) and 0xff).toByte(), ((v shr 8) and 0xff).toByte(), (v and 0xff).toByte())

    // --- фрейминг ClientHello ---

    private fun cryptoFrame(data: ByteArray, offset: Int = 0): ByteArray =
        concat(byteArrayOf(0x06), varint(offset), varint(data.size), data)

    private fun tlsClientHelloToFrames(clientHello: ByteArray, level: Int): Pair<ByteArray, IntArray> {
        if (level == 0) {
            val payload = cryptoFrame(clientHello)
            return payload to intArrayOf(payload.size - clientHello.size + 6, 32, clientHello.size - 38, 16)
        }
        val presets = mapOf(
            1 to intArrayOf(38, Int.MAX_VALUE, 0, 38, 32, 0),
            2 to intArrayOf(38, Int.MAX_VALUE, 0, 38, 37, 0),
            3 to intArrayOf(0, 1, 38, Int.MAX_VALUE, 0, 0),
            4 to intArrayOf(0, 1, 38, Int.MAX_VALUE, 0, 1),
        )
        val p = presets[level] ?: presets[1]!!
        var p1s = p[0]; var p1e = p[1]; var p2s = p[2]; var p2e = p[3]
        val dropTail = p[4]
        val skipZeroes = p[5] == 1
        if (skipZeroes) {
            while (p2s < clientHello.size && clientHello[p2s] == 0.toByte()) p2s++
        }
        if (p1e == Int.MAX_VALUE) p1e = clientHello.size
        if (p2e == Int.MAX_VALUE) p2e = clientHello.size
        val payload = concat(
            cryptoFrame(clientHello.copyOfRange(p1s, p1e), p1s),
            cryptoFrame(clientHello.copyOfRange(p2s, p2e), p2s),
        )
        return payload to intArrayOf(payload.size - dropTail, 16 + dropTail)
    }

    private fun fixCutSettings(cut: IntArray, packetLength: Int, pknLength: Int, payloadLength: Int) {
        if (cut[0] < 20 - pknLength) {
            val toAdd = 20 - pknLength - cut[0]
            cut[0] += toAdd
            cut[1] -= toAdd
        }
        cut[0] += packetLength - payloadLength - 16
    }

    // --- QUIC Initial ---

    private fun quicInitial(
        dcid: ByteArray, scid: ByteArray, token: ByteArray, pkn: ByteArray,
        payload: ByteArray, padTo: Int,
    ): ByteArray {
        fun measure(): Triple<Int, Int, Int> {
            val baseHeader = 8 + dcid.size + scid.size + token.size + pkn.size
            val tag = 16
            var padding = 0
            fun lenBytes() = varintLength(pkn.size + payload.size + padding + tag)
            fun overall() = baseHeader + lenBytes() + payload.size + padding + tag
            var total = overall()
            if (total < padTo) {
                padding = padTo - total
                while (padding > 0 && overall() > padTo) padding--
                if (overall() < padTo) padding++
                total = overall()
            }
            if (pkn.size + payload.size + padding + tag < 20) {
                padding = 20 - pkn.size - payload.size - tag
                total = overall()
            }
            return Triple(total, baseHeader + lenBytes(), padding)
        }
        val (total, headerLen, padding) = measure()
        val header = concat(
            byteArrayOf((0xc0 or (pkn.size - 1)).toByte(), 0x00, 0x00, 0x00, 0x01),
            str8Bytes(dcid), str8Bytes(scid), str8Bytes(token),
            varint(pkn.size + payload.size + padding + 16),
            pkn,
        )
        val initSecret = hmac(SALT, dcid)
        val clientSecret = deriveSecret(initSecret, 32, "client in")
        val quicKey = deriveSecret(clientSecret, 16, "quic key")
        val quicIv = deriveSecret(clientSecret, 12, "quic iv")
        val quicHp = deriveSecret(clientSecret, 16, "quic hp")
        val iv = quicIv.copyOf(12)
        for (i in 0 until pkn.size) {
            val idx = 12 - pkn.size + i
            iv[idx] = (iv[idx].toInt() xor pkn[i].toInt()).toByte()
        }
        val padded = concat(payload, ByteArray(padding))
        val encrypted = aesGcm(quicKey, iv, header, padded)
        val sample = encrypted.copyOfRange(4 - pkn.size, 20 - pkn.size)
        val mask = aesCbcBlock(quicHp, sample)
        val mask0 = mask[0].toInt() and 0x0f
        val out = concat(header, encrypted)
        out[0] = (out[0].toInt() xor mask0).toByte()
        for (i in 0 until pkn.size) {
            out[out.size - pkn.size + i] = (out[out.size - pkn.size + i].toInt() xor mask[1 + i].toInt()).toByte()
        }
        return out
    }

    private fun toAwg(packet: ByteArray, parts: IntArray): String {
        val sb = StringBuilder()
        var offset = 0
        var include = true
        for (part in parts) {
            if (part > 0) {
                if (include) sb.append("<b 0x").append(hex(packet.copyOfRange(offset, offset + part))).append('>')
                else sb.append("<r ").append(part).append('>')
                offset += part
            }
            include = !include
        }
        return sb.toString()
    }
}
