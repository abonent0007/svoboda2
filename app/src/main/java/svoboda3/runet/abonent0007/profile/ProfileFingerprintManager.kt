package svoboda3.runet.abonent0007.profile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Нормализация конфига перед вычислением fingerprint.
 * Версия алгоритма хранится отдельно для будущей миграции.
 */
object ConfigNormalizerForFingerprint {
    const val ALGORITHM_VERSION = 1

    fun normalize(text: String, protocol: String): String {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        // Убрать комментарии inline
        val cleaned = lines.map { line ->
            val idx = line.indexOf('#')
            if (idx >= 0) line.substring(0, idx).trim() else line
        }.filter { it.isNotEmpty() }

        // Сортировать поля внутри секций не меняем порядок секций
        // Нормализация IP/CIDR и hostname делается на уровне текста
        val normalized = cleaned.joinToString("\n") { line ->
            // hostname to lowercase in Endpoint
            if (line.startsWith("Endpoint", ignoreCase = true)) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val hostPort = parts[1].trim()
                    val host = hostPort.substringBeforeLast(":")
                    val port = hostPort.substringAfterLast(":")
                    "${parts[0].trim()} = ${host.lowercase()}:$port"
                } else line
            } else line
        }
        return "$protocol\n$normalized"
    }
}

/**
 * Fingerprint профиля: HMAC-SHA256 с ключом из Android Keystore.
 * Два уровня: exact (весь конфиг) и server (без PrivateKey).
 */
object ProfileFingerprintManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "svoboda.profile.fingerprint.v1"

    private fun getKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(ALIAS, null)?.let { return it as SecretKey }
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
            kg.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            kg.generateKey()
        } catch (e: Exception) {
            null
        }
    }

    enum class MatchType { EXACT_DUPLICATE, SAME_SERVER, POSSIBLE_ENDPOINT_MATCH, NONE }

    data class FingerprintResult(
        val exact: String,
        val server: String,
        val algorithmVersion: Int = ConfigNormalizerForFingerprint.ALGORITHM_VERSION,
    )

    fun compute(configText: String, protocol: String): FingerprintResult? {
        val key = getKey() ?: return null
        val exactInput = ConfigNormalizerForFingerprint.normalize(configText, protocol)
        val exact = hmac(key, exactInput)

        // Server fingerprint: remove PrivateKey line
        val serverInput = exactInput.lines()
            .filterNot { it.trim().startsWith("PrivateKey", ignoreCase = true) }
            .joinToString("\n")
        val server = hmac(key, serverInput)

        return FingerprintResult(exact, server)
    }

    fun matchType(fp1: FingerprintResult, fp2: FingerprintResult): MatchType {
        if (fp1.exact == fp2.exact) return MatchType.EXACT_DUPLICATE
        if (fp1.server == fp2.server) return MatchType.SAME_SERVER
        // POSSIBLE_ENDPOINT_MATCH: could compare endpoints separately, but for now NONE
        return MatchType.NONE
    }

    private fun hmac(key: SecretKey, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
