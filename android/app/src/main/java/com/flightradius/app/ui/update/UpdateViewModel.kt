package com.flightradius.app.ui.update

import androidx.lifecycle.ViewModel
import com.flightradius.app.data.update.AvailableUpdate
import com.flightradius.app.data.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager
) : ViewModel() {
    val candidate: StateFlow<AvailableUpdate?> = manager.bannerCandidate
    fun markShown() = manager.markBannerShown()
}
