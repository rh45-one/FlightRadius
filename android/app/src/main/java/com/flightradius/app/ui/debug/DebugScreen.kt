package com.flightradius.app.ui.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.BuildConfig
import com.flightradius.app.R
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.ui.components.rememberNow
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.extended
import com.flightradius.app.util.log.LogEntry
import com.flightradius.app.util.log.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel()
) {
    val state by viewModel.monitoringState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val now by rememberNow()
    var levelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var query by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val shown = entries.filter { e ->
        (levelFilter == null || e.level == levelFilter) &&
            (query.isBlank() || e.tag.contains(query, true) ||
                e.message.contains(query, true))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.debug_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back))
                }
            }
        )
        // Monitoring state dump.
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.debug_state_title),
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                StateDump(state, now)
            }
        }

        if (BuildConfig.DEBUG) {
            val demoOn by viewModel.demoActive.collectAsStateWithLifecycle()
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(onClick = { viewModel.injectDemo() }) {
                    Text(stringResource(R.string.debug_demo_inject))
                }
                androidx.compose.material3.OutlinedButton(onClick = { viewModel.injectDemo(stale = true) }) {
                    Text(stringResource(R.string.debug_demo_inject_stale))
                }
                if (demoOn) {
                    androidx.compose.material3.OutlinedButton(onClick = { viewModel.clearDemo() }) {
                        Text(stringResource(R.string.debug_demo_clear))
                    }
                }
            }
            Text(
                stringResource(R.string.debug_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Filters + actions.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = levelFilter == null,
                onClick = { levelFilter = null },
                label = { Text(stringResource(R.string.debug_filter_all)) }
            )
            for (lvl in LogLevel.entries) {
                FilterChip(
                    selected = levelFilter == lvl,
                    onClick = { levelFilter = lvl },
                    label = { Text(lvl.name) }
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(
                    shown.joinToString("\n") { e ->
                        "${TIME_FMT.format(Date(e.timeMs))} ${e.level.name} " +
                            "${e.tag}: ${e.message} " +
                            e.fields.entries.joinToString(" ") {
                                "${it.key}=${it.value}"
                            } + (e.throwable?.let { "\n$it" } ?: "")
                    }))
            }) { Text(stringResource(R.string.debug_copy_all)) }
            TextButton(onClick = { viewModel.clear() }) {
                Text(stringResource(R.string.debug_clear))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.debug_search)) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { "${it.timeMs}-${it.tag}-${it.message}" }) { e ->
                LogRow(e)
            }
        }
    }
}

@Composable
private fun StateDump(state: MonitoringState, now: Long) {
    val lines = buildList {
        add("status" to state.status.name)
        add("cycles" to state.cycleCount.toString())
        add("lastLatency" to (state.lastLatencyMs?.let { "${it} ms" } ?: "—"))
        add("failures" to state.consecutiveFailures.toString())
        add("nextCycle" to (state.nextCycleAtMs?.let {
            Format.countdown(now, it)
        } ?: "—"))
        add("openSky" to state.openSkyStatus.name)
        add("dozing" to state.dozing.toString())
        add("wakeLock" to state.wakeLockHeld.toString())
        add("heartbeat" to state.heartbeatHeld.toString())
        add("highPriority" to state.highPriority.toString())
        add("startedFromBg" to state.startedFromBackground.toString())
        add("lastError" to (state.lastError?.message ?: "—"))
        val fix = state.lastSnapshot?.fix
        add("fix" to (fix?.let {
            "%.4f,%.4f %s (%s)".format(it.lat, it.lon,
                Format.age(now, it.timeMs), it.source.name)
        } ?: "—"))
    }
    Column {
        for ((k, v) in lines) {
            Row {
                Text(
                    k,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.45f)
                )
                Text(
                    v,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.55f)
                )
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry) {
    val color = when (e.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.extended.info
        LogLevel.WARN -> MaterialTheme.colorScheme.extended.warning
        LogLevel.ERROR -> MaterialTheme.colorScheme.extended.danger
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(
                TIME_FMT.format(Date(e.timeMs)),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                " ${e.level.name.padEnd(5)} ${e.tag}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Text(
            e.message + e.fields.entries.joinToString("") {
                " ${it.key}=${it.value}"
            },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        e.throwable?.let {
            Text(
                it,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.extended.danger
            )
        }
    }
}
