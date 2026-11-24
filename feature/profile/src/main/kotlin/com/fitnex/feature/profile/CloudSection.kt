package com.fitnex.feature.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.data.sync.CloudSyncState
import com.fitnex.core.data.sync.SyncProblem
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportResult { DONE, FAILED }

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val session: SessionRepository,
    private val sync: CloudSync,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CloudSyncState?> = session.accountUserId
        .flatMapLatest { uid -> uid?.let(sync::state) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _export = MutableStateFlow<ExportResult?>(null)
    val export: StateFlow<ExportResult?> = _export.asStateFlow()

    fun syncNow() = viewModelScope.launch {
        session.accountUserId.first()?.let { sync.syncNow(it) }
    }

    /** Writes the export into the file the user picked with the system "save as" dialog. */
    fun exportTo(uri: Uri) = viewModelScope.launch {
        val uid = session.accountUserId.first() ?: return@launch
        val ok = runCatching {
            val json = sync.exportJson(uid)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
        }.isSuccess
        _export.value = if (ok) ExportResult.DONE else ExportResult.FAILED
    }
}

/** "Copia en la nube" card in Settings (spec §10–§11, Phase 9). */
@Composable
internal fun CloudSection(viewModel: CloudSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    val s = state ?: return
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }

    GlassCard(Modifier.testTag("cloud_section")) {
        SectionHeader(stringResource(R.string.cloud_title))
        Text(stringResource(R.string.cloud_desc), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)

        val last = s.lastSyncedAt?.let { stringResource(R.string.cloud_synced_at, syncedAgo(it, Instant.now())) }
            ?: stringResource(R.string.cloud_never)
        Text(last, style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(top = Spacing.md).testTag("cloud_last"))

        val status = when {
            s.running -> stringResource(R.string.cloud_running)
            s.pendingChanges > 0 -> pluralStringResource(R.plurals.cloud_pending, s.pendingChanges, s.pendingChanges)
            s.problem == null && s.lastSyncedAt != null -> stringResource(R.string.cloud_all_saved)
            else -> null
        }
        status?.let { Text(it, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.testTag("cloud_status")) }
        s.problem?.takeUnless { s.running }?.let { p ->
            Text(stringResource(p.messageRes()), style = FitnexTheme.typography.bodySmall, color = colors.warning, modifier = Modifier.testTag("cloud_problem"))
        }

        FitnexPrimaryButton(
            stringResource(R.string.cloud_sync_now), { viewModel.syncNow() },
            Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("cloud_sync_now"),
            enabled = !s.running,
        )
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = Spacing.md))
        Text(stringResource(R.string.cloud_export_desc), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        FitnexSecondaryButton(
            stringResource(R.string.cloud_export), { saveAs.launch("fitnex-datos-${LocalDate.now()}.json") },
            Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("cloud_export"),
        )
        export?.let {
            Text(
                stringResource(if (it == ExportResult.DONE) R.string.cloud_export_done else R.string.cloud_export_failed),
                style = FitnexTheme.typography.bodySmall,
                color = if (it == ExportResult.DONE) colors.textSecondary else colors.error,
            )
        }
    }
}

/** One line for the Nube row in Ajustes: is my data safe, and since when. */
@Composable
internal fun cloudStatusText(viewModel: CloudSyncViewModel = hiltViewModel()): String? {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val st = s ?: return null
    return when {
        st.running -> stringResource(R.string.cloud_running)
        st.problem != null -> stringResource(st.problem!!.messageRes())
        st.pendingChanges > 0 -> pluralStringResource(R.plurals.cloud_pending, st.pendingChanges, st.pendingChanges)
        st.lastSyncedAt != null -> stringResource(R.string.cloud_saved_ago, syncedAgo(st.lastSyncedAt!!, Instant.now()))
        else -> stringResource(R.string.cloud_never)
    }
}

private fun SyncProblem.messageRes() = when (this) {
    SyncProblem.OFFLINE -> R.string.cloud_problem_offline
    SyncProblem.SESSION_EXPIRED -> R.string.cloud_problem_session
    SyncProblem.RATE_LIMITED -> R.string.cloud_problem_rate
    SyncProblem.SERVER -> R.string.cloud_problem_server
    SyncProblem.NOT_CONFIGURED -> R.string.cloud_problem_config
}

/** Always Spanish, whatever the device language: "hace un momento", "hace 5 min", "hoy a las 09:40", "18 nov, 21:05". */
internal fun syncedAgo(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = java.time.Duration.between(at, now).toMinutes()
    val local = at.atZone(zone)
    return when {
        minutes < 1 -> "hace un momento"
        minutes < 60 -> "hace $minutes min"
        local.toLocalDate() == now.atZone(zone).toLocalDate() -> "hoy a las " + local.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> local.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es")))
    }
}