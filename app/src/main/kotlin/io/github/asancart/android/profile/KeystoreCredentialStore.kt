package io.github.asancart.android.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.asancart.android.core.profile.CredentialStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device credentials (REQ-DEV-030) encrypted with AES-GCM under a key that never leaves the Android
 * Keystore (no Google services, REQ-APP-041), stored per Profile id in app-private preferences,
 * which backups never copy (`data_extraction_rules.xml`). A credential that can't be decrypted (the
 * key is gone) reads as none: the Profile then gets `401`-like treatment and is paired again
 * (REQ-APP-030). Written with `commit()`, so a pairing is on disk before the app says it worked; ACRA's
 * crash sender reads it from its own process (REQ-OPS-013).
 */
// commit(), not apply(): see above.
@SuppressLint("ApplySharedPref")
class KeystoreCredentialStore(private val prefs: SharedPreferences, private val cipher: SecretCipher) : CredentialStore {
    override fun get(profileId: String): String? {
        val sealed = prefs.getString(KEY_PREFIX + profileId, null) ?: return null
        return try {
            cipher.decrypt(Base64.decode(sealed, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun put(profileId: String, credential: String) {
        val sealed = Base64.encodeToString(cipher.encrypt(credential.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString(KEY_PREFIX + profileId, sealed).commit()
    }

    override fun delete(profileId: String) {
        prefs.edit().remove(KEY_PREFIX + profileId).commit()
    }

    override fun retainOnly(profileIds: Set<String>) {
        val stale = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) && it.removePrefix(KEY_PREFIX) !in profileIds }
        if (stale.isEmpty()) return
        val e = prefs.edit()
        stale.forEach(e::remove)
        e.commit()
    }

    override fun toString(): String = "KeystoreCredentialStore"

    companion object {
        private const val KEY_PREFIX = "credential:"

        fun of(context: Context) = KeystoreCredentialStore(
            context.getSharedPreferences("device_credentials", Context.MODE_PRIVATE),
            AndroidKeystoreCipher(),
        )
    }
}

/** Seals and opens small secrets. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws [GeneralSecurityException] when [sealed] can't be opened. */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a key generated in, and kept by, the Android Keystore. Output: IV length (1 byte),
 * IV, ciphertext with its 128-bit tag.
 */
class AndroidKeystoreCipher(private val alias: String = "asancart-device-credentials") : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey())
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val key = existingKey() ?: throw GeneralSecurityException("no key")
        if (sealed.isEmpty()) throw GeneralSecurityException("empty")
        val ivLength = sealed[0].toInt()
        if (ivLength <= 0 || sealed.size < 1 + ivLength + TAG_BITS / 8) throw GeneralSecurityException("too short")
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 1, ivLength))
        return c.doFinal(sealed, 1 + ivLength, sealed.size - 1 - ivLength)
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun newKey(): SecretKey = synchronized(LOCK) {
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val LOCK = Any()
    }
}
