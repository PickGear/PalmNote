package com.palmnote.data.lock

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.ui.lock.AppLockState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppLockManagerTest {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var appLockManager: AppLockManager

    @Before
    fun setup() {
        preferencesManager = mockk(relaxed = true)
        every { preferencesManager.getEncryptedPin() } returns ""
        every { preferencesManager.isAppLockEnabled() } returns false
        appLockManager = AppLockManager(mockk(relaxed = true), preferencesManager)
    }

    @Test
    fun `isLockEnabled returns false by default`() {
        assertFalse(appLockManager.isLockEnabled())
    }

    @Test
    fun `isLockEnabled returns true when enabled`() {
        every { preferencesManager.isAppLockEnabled() } returns true
        assertTrue(appLockManager.isLockEnabled())
    }

    @Test
    fun `hasPin returns false when no pin set`() {
        every { preferencesManager.getEncryptedPin() } returns ""
        assertFalse(appLockManager.hasPin())
    }

    @Test
    fun `hasPin returns true when pin is set`() {
        every { preferencesManager.getEncryptedPin() } returns "encrypted_pin_data"
        assertTrue(appLockManager.hasPin())
    }

    @Test
    fun `verifyPin empty pin returns true`() {
        every { preferencesManager.getEncryptedPin() } returns ""
        val result = appLockManager.verifyPin("123456")
        assertTrue(result)
    }

    @Test
    fun `lock sets state to Locked`() {
        every { preferencesManager.isAppLockEnabled() } returns true
        every { preferencesManager.getEncryptedPin() } returns "encrypted"
        appLockManager.lock()
        assertEquals(AppLockState.Locked, appLockManager.lockState.value)
    }

    @Test
    fun `lock when disabled does nothing`() {
        every { preferencesManager.isAppLockEnabled() } returns false
        appLockManager.lock()
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)
    }

    @Test
    fun `setEnabled true when no pin sets NeedSetup`() {
        every { preferencesManager.getEncryptedPin() } returns ""
        appLockManager.setEnabled(true)
        assertEquals(AppLockState.NeedSetup, appLockManager.lockState.value)
    }

    @Test
    fun `setEnabled false sets Unlocked`() {
        appLockManager.setEnabled(false)
        assertEquals(AppLockState.Unlocked, appLockManager.lockState.value)
    }

    @Test
    fun `setEnabled calls preferencesManager`() {
        appLockManager.setEnabled(true)
        verify { preferencesManager.setAppLockEnabledSync(true) }
    }
}
