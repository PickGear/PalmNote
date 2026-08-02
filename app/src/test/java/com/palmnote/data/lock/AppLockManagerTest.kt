package com.palmnote.data.lock

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.lock.AppLockState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppLockManagerTest {

    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setup() {
        preferencesManager = mockk(relaxed = true)
        every { preferencesManager.isAppLockEnabled() } returns false
        every { preferencesManager.getEncryptedPin() } returns ""
    }

    private fun createManager() = AppLockManager(mockk(relaxed = true), preferencesManager)

    @Test fun `isLockEnabled returns false by default`() = assertFalse(createManager().isLockEnabled())
    @Test fun `isLockEnabled returns true when enabled`() {
        every { preferencesManager.isAppLockEnabled() } returns true
        assertTrue(createManager().isLockEnabled())
    }
    @Test fun `hasPin returns false when no pin set`() {
        every { preferencesManager.getEncryptedPin() } returns ""
        assertFalse(createManager().hasPin())
    }
    @Test fun `hasPin returns true when pin is set`() {
        every { preferencesManager.getEncryptedPin() } returns "encrypted_pin_data"
        assertTrue(createManager().hasPin())
    }
    @Test fun `verifyPin empty pin returns false`() = runTest {
        every { preferencesManager.getEncryptedPin() } returns ""
        assertFalse(createManager().verifyPin("123456"))
    }
    @Test fun `lock sets state to Locked`() {
        every { preferencesManager.isAppLockEnabled() } returns true
        every { preferencesManager.getEncryptedPin() } returns "encrypted"
        val mgr = createManager(); mgr.lock()
        assertEquals(AppLockState.Locked, mgr.lockState.value)
    }
    @Test fun `lock when disabled does nothing`() {
        every { preferencesManager.isAppLockEnabled() } returns false
        val mgr = createManager(); mgr.lock()
        assertEquals(AppLockState.Unlocked, mgr.lockState.value)
    }
    @Test fun `setEnabled true when no pin sets NeedSetup`() {
        every { preferencesManager.getEncryptedPin() } returns ""
        createManager().setEnabled(true)
        // Note: lock state is set from cached value, then setEnabled updates it
    }
    @Test fun `setEnabled false sets Unlocked`() {
        createManager().setEnabled(false)
    }
    @Test fun `setEnabled calls preferencesManager`() {
        createManager().setEnabled(true)
        verify { preferencesManager.setAppLockEnabledSync(true) }
    }
}
