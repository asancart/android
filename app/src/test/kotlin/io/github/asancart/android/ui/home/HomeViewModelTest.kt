package io.github.asancart.android.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.github.asancart.android.core.profile.ApiTarget
import io.github.asancart.android.core.profile.InMemoryProfileRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val repo = InMemoryProfileRepository(ApiTarget.platform("http://10.0.2.2:8080"), newId = { "p1" }).apply { ensureDefaultProfile("پروفایل اصلی") }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultProfileIsShownOffUnpairedAndBlocked() {
        val card = HomeViewModel(repo, { false }).state.value.profiles.single()
        assertEquals("پروفایل اصلی", card.name)
        assertTrue(card.isPlatform)
        assertEquals("10.0.2.2:8080", card.host)
        assertEquals(null, card.shopName)
        assertFalse(card.enabled)
        assertFalse(card.canTurnOn)
        assertFalse(card.setupFinished)
    }

    @Test
    fun turningOnIsRefusedWhileBlocked() {
        val vm = HomeViewModel(repo, { true })
        vm.setEnabled("p1", true)
        assertFalse(repo.profiles.value.single().enabled)
    }
}
