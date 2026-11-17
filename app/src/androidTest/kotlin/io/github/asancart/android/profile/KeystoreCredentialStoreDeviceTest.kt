package io.github.asancart.android.profile

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore

/** T4.3 on a device: credentials sealed with a key held by the Android Keystore. */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "asancart-test-credentials"
    private val prefs = context.getSharedPreferences("device_credentials_test", Context.MODE_PRIVATE)

    @After
    fun clean() {
        prefs.edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun aCredentialRoundTripsAndIsNeverStoredInTheClear() {
        val store = KeystoreCredentialStore(prefs, AndroidKeystoreCipher(alias))
        store.put("p1", "dvc_device_test_credential_123")
        assertEquals("dvc_device_test_credential_123", store.get("p1"))
        // Another instance, as after a restart or in ACRA's process, reads it with the same key.
        assertEquals("dvc_device_test_credential_123", KeystoreCredentialStore(prefs, AndroidKeystoreCipher(alias)).get("p1"))
        for (v in prefs.all.values) assertFalse(v.toString(), v.toString().contains("dvc_device"))
    }

    @Test
    fun eachSealUsesItsOwnIvAndTamperingIsRefused() {
        val cipher = AndroidKeystoreCipher(alias)
        val a = cipher.encrypt("same".toByteArray())
        val b = cipher.encrypt("same".toByteArray())
        assertNotEquals(Base64.encodeToString(a, Base64.NO_WRAP), Base64.encodeToString(b, Base64.NO_WRAP))
        assertArrayEquals("same".toByteArray(), cipher.decrypt(a))
        a[a.size - 1] = (a[a.size - 1].toInt() xor 1).toByte()
        try {
            cipher.decrypt(a)
            fail("a changed ciphertext must not open")
        } catch (expected: GeneralSecurityException) {
        }
    }

    @Test
    fun withoutItsKeyACredentialReadsAsNone() {
        val store = KeystoreCredentialStore(prefs, AndroidKeystoreCipher(alias))
        store.put("p1", "dvc_x")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertNull(store.get("p1"))
    }
}
