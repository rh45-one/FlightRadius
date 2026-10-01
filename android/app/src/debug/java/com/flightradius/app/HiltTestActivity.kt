package com.flightradius.app

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Debug-only empty Hilt-enabled host for Compose tests that need `hiltViewModel()`. */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
