package io.github.asancart.android.core.profile

import java.util.concurrent.ConcurrentHashMap

/**
 * Device credentials (REQ-DEV-001, REQ-DEV-030) by Profile id, kept apart from the Profile so a
 * Profile can be stored, shown and logged without one (REQ-OPS-003). The app keeps them encrypted
 * with a key in the Android Keystore; nothing here logs or prints a credential.
 */
interface CredentialStore {
    fun get(profileId: String): String?

    fun put(profileId: String, credential: String)

    fun delete(profileId: String)

    /** Deletes every credential whose Profile id is not in [profileIds]. */
    fun retainOnly(profileIds: Set<String>)
}

/** For tests and demos. */
class InMemoryCredentialStore : CredentialStore {
    private val map = ConcurrentHashMap<String, String>()

    override fun get(profileId: String): String? = map[profileId]

    override fun put(profileId: String, credential: String) {
        map[profileId] = credential
    }

    override fun delete(profileId: String) {
        map.remove(profileId)
    }

    override fun retainOnly(profileIds: Set<String>) {
        map.keys.retainAll(profileIds)
    }

    override fun toString(): String = "InMemoryCredentialStore(${map.size} credentials)"
}
