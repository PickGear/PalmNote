package com.palmnote.ui.search

import kotlinx.coroutines.flow.StateFlow

interface ModuleSearchViewModel<T> {
    val searchQuery: StateFlow<String>
    val searchResults: StateFlow<List<T>>
    val isSearching: StateFlow<Boolean>
    
    fun onSearchQueryChanged(query: String)
    fun clearSearch()
}
