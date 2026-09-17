package com.fitnesslemon.app.ui.common

/** Shared presentation state for feature ViewModels. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val value: T, val fromCache: Boolean = false) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
}
