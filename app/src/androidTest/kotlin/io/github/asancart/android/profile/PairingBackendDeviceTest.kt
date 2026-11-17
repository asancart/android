package io.github.asancart.android.profile

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asancart.android.AppContainer
import io.github.asancart.android.BuildConfig
import io.github.asancart.android.core.deviceapi.ApiResult
import io.github.asancart.android.core.deviceapi.ErrorCode
import io.github.asancart.android.core.deviceapi.HttpDeviceApi
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.JsonFileProfileRepository
import io.github.asancart.android.core.profile.Profile
import io.github.asancart.android.core.profile.ProfileConnections
import io.github.asancart.android.core.profile.Removal
import io.github.asancart.android.core.profile.TargetUrl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/**
 * T4.3 on a device against a running backend: the debug build's Platform address
 * (`asancart.platformApiUrl`, `http://10.0.2.2:8080` on the emulator) and a custom target typed as a
 * user would. A script seeds a Shop with a VERIFIED Card and two pairing codes and passes them as
 * instrumentation arguments `platformPairingCode`, `customPairingCode` and `customBaseUrl`; without
 * them this test is skipped.
 */
@RunWith(AndroidJUnit4::class)
class PairingBackendDeviceTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "pairing-backend-test").apply { deleteRecursively(); mkdirs() }
    private val prefs = context.getSharedPreferences("device_credentials_backend_test", Context.MODE_PRIVATE)
    private val alias = "asancart-backend-test-credentials"

    @After
    fun clean() {
        dir.deleteRecursively()
        prefs.edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun pairWithThePlatformAndACustomTargetThenUnpairOnRemoval() = runBlocking {
        val platformCode = args.getString("platformPairingCode")
        val customCode = args.getString("customPairingCode")
        val customUrl = args.getString("customBaseUrl")
        assumeTrue("no backend was prepared for this run", platformCode != null && customCode != null && customUrl != null)

        val platform = ApiTarget.platform(BuildConfig.PLATFORM_API_BASE_URL)
        val profiles = JsonFileProfileRepository(File(dir, "profiles.json"), platform)
        val credentials = KeystoreCredentialStore(prefs, AndroidKeystoreCipher(alias))
        val apiFor = { t: ApiTarget, c: String? -> HttpDeviceApi(t.baseUrl, c, AppContainer.USER_AGENT) }
        val connections = ProfileConnections(profiles, credentials, apiFor, BuildConfig.VERSION_NAME, defaultProfileName = { "main" })
        val main = profiles.ensureDefaultProfile("main")

        // A wrong code: the backend's PAIRING_INVALID, its Persian message and a request id.
        val wrong = connections.pair(main.id, "2222-2222", Build.MODEL) as ApiResult.Error
        assertEquals(ErrorCode.PAIRING_INVALID, wrong.code)
        assertTrue(wrong.message, wrong.message.contains("کد اتصال"))
        assertFalse(profiles.profiles.value.single().paired)

        // The Platform, preselected (REQ-APP-022), with the code typed in lower case (REQ-DEV-001).
        val paired = (connections.pair(main.id, platformCode!!.lowercase(), Build.MODEL) as ApiResult.Ok<Profile>).value
        val c = paired.connection!!
        assertTrue(c.cards.isNotEmpty())
        assertTrue(c.banks.map { it.id }.containsAll(c.cards.map { it.bankId }))
        assertTrue(credentials.get(main.id)!!.startsWith("dvc_"))
        assertTrue(connections.refreshCards(main.id) is ApiResult.Ok)

        // Another service, by the address the user typed.
        val typed = TargetUrl.parse(customUrl!!, allowHttp = BuildConfig.DEBUG) as TargetUrl.Valid
        val other = profiles.add("other", ApiTarget.custom(typed))
        val otherPaired = (connections.pair(other.id, customCode!!, Build.MODEL) as ApiResult.Ok<Profile>).value
        assertNotNull(otherPaired.connection)
        assertEquals(typed.baseUrl, otherPaired.target.baseUrl)

        // Profiles and credentials survive a restart.
        val again = JsonFileProfileRepository(File(dir, "profiles.json"), platform)
        assertEquals(profiles.profiles.value, again.profiles.value)

        // Removal calls Unpair: the old credential is refused from then on (REQ-DEV-009, REQ-APP-040).
        val otherCredential = credentials.get(other.id)
        assertEquals(Removal.Removed(deviceStillListed = false), connections.remove(other.id, evenIfUnpairFails = false))
        val after = HttpDeviceApi(typed.baseUrl, otherCredential, AppContainer.USER_AGENT).cards() as ApiResult.Error
        assertTrue(after.unauthorized)

        val mainCredential = credentials.get(main.id)
        assertEquals(Removal.Removed(deviceStillListed = false), connections.remove(main.id, evenIfUnpairFails = false))
        assertTrue((HttpDeviceApi(platform.baseUrl, mainCredential, AppContainer.USER_AGENT).cards() as ApiResult.Error).unauthorized)
        // REQ-APP-021: never empty.
        assertEquals(1, profiles.profiles.value.size)
        assertFalse(profiles.profiles.value.single().paired)
    }
}
