package com.palmnote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@Composable
inline fun <reified T : ViewModel> simpleViewModel(
    owner: ViewModelStoreOwner? = null,
    noinline factory: () -> T
): T {
    val viewModelStoreOwner = owner ?: checkNotNull(LocalViewModelStoreOwner.current)
    val key = T::class.qualifiedName ?: T::class.simpleName ?: ""
    return remember(viewModelStoreOwner) {
        ViewModelProvider(viewModelStoreOwner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
        })[key, T::class.java]
    }
}
