package com.palmnote.feature.vault.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
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

data class VaultEditUiState(
    val lockState: LockState = LockState.LOCKED,
    val requireAuth: Boolean = true,
    val biometricEnabled: Boolean = false,
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val entry: VaultEntry? = null
)

@HiltViewModel
class VaultEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lockManager: VaultLockManager,
    private val repository: VaultRepository,
    preferencesManager: PreferencesManager
) : ViewModel() {

    val entryId: Long? = savedStateHandle["entryId"]
    private val entryState = MutableStateFlow<VaultEntry?>(null)
    private val loadingState = MutableStateFlow(true)

    val uiState: StateFlow<VaultEditUiState> = combine(
        lockManager.state,
        preferencesManager.vaultRequireAuth,
        entryState,
        loadingState
    ) { lock, requireAuth, entry, loading ->
        VaultEditUiState(
            lockState = lock,
            requireAuth = requireAuth,
            biometricEnabled = lockManager.biometricEnabled(),
            isEdit = entryId != null,
            loading = loading,
            entry = entry
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), VaultEditUiState())

    init {
        lockManager.initialize()
        if (lockManager.isNoLockMode()) {
            viewModelScope.launch {
                if (lockManager.state.value == VaultLockManager.LockState.LOCKED) {
                    lockManager.unlockNoLock()
                }
            }
        }
        if (entryId != null) {
            viewModelScope.launch {
                loadingState.value = true
                entryState.value = repository.getEntry(entryId)
                loadingState.value = false
            }
        } else {
            loadingState.value = false
        }
    }

    /** 详情/编辑页明文展示用。 */
    fun passwordForDisplay(entry: VaultEntry): String? = repository.decryptPassword(entry)

    fun setupPin(pin: String) {
        viewModelScope.launch { lockManager.setup(pin) }
    }

    fun unlock(pin: String) {
        viewModelScope.launch { lockManager.unlock(pin) }
    }

    fun createBioDecryptCipher(): javax.crypto.Cipher? = lockManager.createBioDecryptCipher()

    fun unlockWithBiometric(cipher: javax.crypto.Cipher) {
        viewModelScope.launch { lockManager.unlockWithBiometric(cipher) }
    }

    fun lock() {
        lockManager.lock()
    }

    fun save(
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        category: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val ok = if (entryId == null) {
                repository.create(title, username, password, url, notes, category) != null
            } else {
                repository.update(entryId, title, username, password, url, notes, category)
            }
            onResult(ok)
        }
    }
}
