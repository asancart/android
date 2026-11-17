package io.github.asancart.android.profile

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.GeneralSecurityException

/**
 * The store around a cipher. The JVM has no Android Keystore, so a stand-in cipher seals here; the
 * real one runs on a device (androidTest KeystoreCredentialStoreDeviceTest).
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreCredentialStoreTest {
    /** Reverses and tags the bytes: enough to show the stored text is not the credential. */
    private class StandInCipher : SecretCipher {
        var broken = false

        override fun encrypt(plain: ByteArray) = byteArrayOf(42) + plain.reversedArray()

        override fun decrypt(sealed: ByteArray): ByteArray {
            if (broken || sealed.firstOrNull() != 42.toByte()) throw GeneralSecurityException("can't open")
            return sealed.copyOfRange(1, sealed.size).reversedArray()
        }
    }

    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("creds-test", Context.MODE_PRIVATE)
    private val cipher = StandInCipher()
    private val store = KeystoreCredentialStore(prefs, cipher)

    @Test
    fun aCredentialIsStoredSealedPerProfile() {
        store.put("p1", "dvc_secret_one")
        store.put("p2", "dvc_secret_two")
        assertEquals("dvc_secret_one", store.get("p1"))
        assertEquals("dvc_secret_two", KeystoreCredentialStore(prefs, cipher).get("p2"))
        for (v in prefs.all.values) assertFalse(v.toString(), v.toString().contains("dvc_secret"))
        assertNull(store.get("p3"))
    }

    @Test
    fun deleteAndRetainOnly() {
        store.put("p1", "a")
        store.put("p2", "b")
        store.put("p3", "c")
        store.delete("p1")
        store.retainOnly(setOf("p2"))
        assertNull(store.get("p1"))
        assertEquals("b", store.get("p2"))
        assertNull(store.get("p3"))
    }

    @Test
    fun aCredentialThatCannotBeOpenedReadsAsNone() {
        store.put("p1", "a")
        cipher.broken = true
        assertNull(store.get("p1"))
        prefs.edit().putString("credential:p2", "%%% not base64").commit()
        cipher.broken = false
        assertNull(store.get("p2"))
    }

    @Test
    fun itNeverPrintsACredential() {
        store.put("p1", "dvc_secret")
        assertFalse(store.toString().contains("dvc_secret"))
    }
}
