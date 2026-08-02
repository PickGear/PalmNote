package com.palmnote.feature.vault.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.feature.vault.VaultClipboardManager
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.feature.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultDetailUiState(
    val lockState: LockState = LockState.LOCKED,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val entry: VaultEntry? = null,
    val isLoading: Boolean = true,
    val deleted: Boolean = false
)

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    private val clipboardManager: VaultClipboardManager,
    preferencesManager: PreferencesManager
) : ViewModel() {

    private val entryId: Long = savedStateHandle["entryId"] ?: INVALID_ID
    private val entryState = MutableStateFlow<VaultEntry?>(null)
    private val loadingState = MutableStateFlow(true)
    private val deletedState = MutableStateFlow(false)

    val uiState: StateFlow<VaultDetailUiState> = combine(
        lockManager.state,
        preferencesManager.vaultRequireAuth,
        entryState,
        loadingState,
        deletedState
    ) { lock, requireAuth, entry, loading, deleted ->
        VaultDetailUiState(lock, requireAuth, lockManager.biometricEnabled(), entry, loading, deleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VaultDetailUiState())

    init {
        lockManager.initialize()
        if (lockManager.isNoLockMode()) {
            viewModelScope.launch {
                if (lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                    lockManager.unlockNoLock()
                }
            }
        }
        reload()
    }

    fun reload() {
        if (entryId == INVALID_ID) {
            loadingState.value = false
            return
        }
        viewModelScope.launch {
            loadingState.value = true
            entryState.value = repository.getEntry(entryId)
            loadingState.value = false
        }
    }

    fun setupPin(pin: String) {
        viewModelScope.launch { lockManager.setup(pin) }
    }

    fun unlock(pin: String) {
        viewModelScope.launch { lockManager.unlock(pin) }
    }

    fun biometricEnabled(): Boolean = lockManager.biometricEnabled()

    fun createBioDecryptCipher(): javax.crypto.Cipher? = lockManager.createBioDecryptCipher()

    fun unlockWithBiometric(cipher: javax.crypto.Cipher) {
        viewModelScope.launch { lockManager.unlockWithBiometric(cipher) }
    }

    fun lock() {
        lockManager.lock()
    }

    fun copyUsername(entry: VaultEntry): Boolean {
        if (entry.username.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.username)
        return true
    }

    fun copyPassword(entry: VaultEntry): Boolean {
        val password = repository.decryptPassword(entry) ?: return false
        if (password.isEmpty()) return false
        clipboardManager.copy(entry.title, password)
        return true
    }

    /** 详情页明文展示用（同步解密，需已解锁）。 */
    fun passwordForDisplay(entry: VaultEntry): String? = repository.decryptPassword(entry)

    fun copyUrl(entry: VaultEntry): Boolean {
        if (entry.url.isEmpty()) return false
        clipboardManager.copy(entry.title, entry.url)
        return true
    }

    fun delete() {
        viewModelScope.launch {
            entryState.value?.let { repository.delete(it) }
            deletedState.value = true
        }
    }

    private companion object {
        const val INVALID_ID = -1L
    }
}
