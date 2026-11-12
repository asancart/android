package io.github.asancart.android.core.heartbeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeartbeatSettingTest {
    @Test
    fun `off sends interval 0 (REQ-DEV-022)`() {
        assertEquals(0, HeartbeatSetting(enabled = false, intervalSec = 300).wireIntervalSec)
        assertEquals(300, HeartbeatSetting(enabled = true, intervalSec = 300).wireIntervalSec)
    }

    @Test
    fun `interval stays within 60 to 86400 seconds (REQ-DEV-003)`() {
        assertFailsWith<IllegalArgumentException> { HeartbeatSetting(enabled = true, intervalSec = 59) }
        assertFailsWith<IllegalArgumentException> { HeartbeatSetting(enabled = true, intervalSec = 86_401) }
        HeartbeatSetting(enabled = true, intervalSec = 60)
        HeartbeatSetting(enabled = true, intervalSec = 86_400)
    }
}
