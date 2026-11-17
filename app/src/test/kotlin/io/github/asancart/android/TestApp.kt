package io.github.asancart.android

import io.github.asancart.android.core.deviceapi.StubDeviceApi
import io.github.asancart.android.core.profile.InMemoryCredentialStore

/**
 * The app under Robolectric (robolectric.properties): every target answers from one in-memory
 * [StubDeviceApi], and credentials stay in memory, as the JVM has no Android Keystore.
 */
class TestApp : AsancartApp() {
    val deviceApi = StubDeviceApi()

    override fun createContainer(): AppContainer =
        AppContainer(this, deviceApiFor = { _, _ -> deviceApi }, credentials = InMemoryCredentialStore())
}
