package svoboda3.runet.abonent0007.secure

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM шифрование через Android Keystore.
 * Ключ не извлекается из устройства; фоновое восстановление VPN работает без биометрии.
 */
object SecureConfigStorage {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "svoboda.config.master.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SIZE = 12

    private fun getKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(ALIAS, null)?.let { return it as SecretKey }
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            kg.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            kg.generateKey()
        } catch (e: Exception) {
            null
        }
    }

    fun encrypt(plaintext: String): String? {
        if (plaintext.isBlank()) return null
        val key = getKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val out = ByteArray(iv.size + ct.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(ct, 0, out, iv.size, ct.size)
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(blob: String): String? {
        if (blob.isBlank()) return null
        val key = getKey() ?: return null
        return try {
            val data = Base64.decode(blob, Base64.NO_WRAP)
            if (data.size <= IV_SIZE) return null
            val iv = data.copyOfRange(0, IV_SIZE)
            val ct = data.copyOfRange(IV_SIZE, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun isEncrypted(blob: String): Boolean {
        if (blob.isBlank()) return false
        if (blob.contains("[Interface]", ignoreCase = true)) return false
        return runCatching { Base64.decode(blob, Base64.NO_WRAP); true }.getOrDefault(false)
    }
}
