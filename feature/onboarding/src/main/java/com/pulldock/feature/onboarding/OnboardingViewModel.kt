package com.pulldock.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.repository.ConsentState
import com.pulldock.domain.download.usecase.AcknowledgeConsent
import com.pulldock.domain.download.usecase.ObserveConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable onboarding UI state. */
data class OnboardingUiState(
    /** Whether the acknowledgement write is in flight; button disabled meanwhile. */
    val acknowledging: Boolean = false,
)

/** One-shot onboarding events. */
sealed interface OnboardingEvent {
    /** Consent acknowledged for the required version; navigation may proceed. */
    data object Acknowledged : OnboardingEvent
}

/**
 * Consent and capability explanation (US1). The ViewModel never invents acknowledgement
 * state: it surfaces the persisted [ConsentState] and persists the user's action.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observeConsent: ObserveConsent,
    private val acknowledgeConsent: AcknowledgeConsent,
) : ViewModel() {

    /** Persisted consent; null until the first emission arrives (loading). */
    val consent: StateFlow<ConsentState?> = observeConsent()
        .map<ConsentState, ConsentState?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Acknowledges the current legal version; idempotent via the persisted store. */
    fun acknowledge() {
        val state = consent.value ?: return
        if (_uiState.value.acknowledging) return
        _uiState.update { it.copy(acknowledging = true) }
        viewModelScope.launch {
            try {
                acknowledgeConsent(state.requiredVersion)
                _events.tryEmit(OnboardingEvent.Acknowledged)
            } finally {
                _uiState.update { it.copy(acknowledging = false) }
            }
        }
    }
}
