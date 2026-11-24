package com.fitnex.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.ai.AiRepository
import com.fitnex.core.data.ai.AiResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.AiScreenContext
import com.fitnex.core.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiChatUiState(
    val loading: Boolean = true,
    val consent: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val failure: AiFailure? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val ai: AiRepository,
) : ViewModel() {
    val context: AiScreenContext? = savedState.get<String>("sourceScreen")?.let(AiScreenContext::fromKey)

    /**
     * Opened from an action such as "Analizar mi progreso": its question is sent once (not again
     * after a rotation or when coming back to the chat).
     */
    fun sendRequestedOnce(text: String) {
        if (savedState.get<Boolean>("ask") != true || savedState.get<Boolean>(ASKED) == true) return
        savedState[ASKED] = true
        send(text)
    }

    private companion object {
        const val ASKED = "askHandled"
    }
    private val sending = MutableStateFlow(false)
    private val failure = MutableStateFlow<AiFailure?>(null)
    private var lastText: String? = null

    val state: StateFlow<AiChatUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            // A "Entrenar juntos" profile uses the account owner's permission (given in Ajustes › Cuenta).
            val owner = if (com.fitnex.core.data.profile.TrainingProfileRules.isCompanion(uid)) {
                profiles.observe(com.fitnex.core.data.profile.TrainingProfileRules.accountOf(uid))
            } else {
                kotlinx.coroutines.flow.flowOf(null)
            }
            combine(profiles.observe(uid), owner, ai.observeMessages(uid), sending, failure) { p, o, msgs, s, f ->
                AiChatUiState(false, p?.hasAiConsent == true || o?.hasAiConsent == true, msgs, s, f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatUiState())

    fun grantConsent() { viewModelScope.launch { session.currentUserId.first()?.let { profiles.setAiConsent(it, true) } } }

    fun send(text: String) {
        if (text.isBlank() || sending.value) return
        lastText = text
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            sending.value = true
            failure.value = null
            val r = ai.send(uid, text, context)
            failure.value = (r as? AiResult.Failed)?.reason
            sending.value = false
        }
    }

    /** Asks the unanswered question again; it is already in the conversation, so it is not stored twice. */
    fun retry() {
        val text = lastText ?: return
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            sending.value = true
            failure.value = null
            failure.value = (ai.send(uid, text, context, alreadyStored = true) as? AiResult.Failed)?.reason
            sending.value = false
        }
    }

    fun clear() { viewModelScope.launch { session.currentUserId.first()?.let { ai.clear(it) }; failure.value = null } }
}

@Composable
internal fun AiChatScreen(state: AiChatUiState, vm: AiChatViewModel, onBack: () -> Unit) {
    val colors = FitnexTheme.colors
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.sending) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        FitnexHeader(
            title = stringResource(R.string.ai_title), overline = stringResource(R.string.ai_overline), onBack = onBack,
            actions = {
                if (state.messages.isNotEmpty()) TextButton(onClick = vm::clear, modifier = Modifier.testTag("ai_clear")) { Text(stringResource(R.string.ai_clear), color = colors.accent) }
            },
        )
        vm.context?.let {
            Text(
                stringResource(R.string.ai_context_label, stringResource(it.labelRes())), style = FitnexTheme.typography.labelSmall,
                color = colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.gutter).testTag("ai_context_label"),
            )
        }
        if (!state.loading && !state.consent) {
            GlassCard(Modifier.padding(Spacing.gutter).testTag("ai_consent")) {
                SectionHeader(stringResource(R.string.ai_consent_title))
                Text(stringResource(R.string.ai_consent_message), style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary)
                FitnexPrimaryButton(stringResource(R.string.ai_consent_accept), vm::grantConsent, Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("ai_consent_accept"))
            }
            Disclaimer()
            return@Column
        }

        // "Analizar mi progreso" and similar actions: their question goes out as soon as the chat is ready.
        val requested = stringResource(suggestionsFor(vm.context).first().second)
        LaunchedEffect(state.loading, state.consent) {
            if (!state.loading && state.consent) vm.sendRequestedOnce(requested)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ai_messages"), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.messages.isEmpty()) {
                item { Text(stringResource(R.string.ai_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary) }
                item { Suggestions(vm) }
            }
            items(state.messages, key = { it.id }) { m -> Bubble(m) }
            if (state.sending) item { Text(stringResource(R.string.ai_thinking), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.testTag("ai_thinking")) }
            state.failure?.let { f ->
                item {
                    GlassCard(Modifier.fillMaxWidth().testTag("ai_error")) {
                        Text(stringResource(f.messageRes()), style = FitnexTheme.typography.bodyMedium, color = colors.warning)
                        if (f == AiFailure.NO_CONNECTION || f == AiFailure.UNAVAILABLE || f == AiFailure.RATE_LIMITED) {
                            TextButton(onClick = vm::retry) { Text(stringResource(R.string.ai_retry), color = colors.accent) }
                        }
                    }
                }
            }
            item { Disclaimer() }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FitnexTextField(value = input, onValueChange = { input = it.take(2000) }, label = stringResource(R.string.ai_input), modifier = Modifier.weight(1f).testTag("ai_input"))
            FitnexPrimaryButton(stringResource(R.string.ai_send), { vm.send(input); input = "" }, Modifier.testTag("ai_send"), enabled = input.isNotBlank() && !state.sending)
        }
    }
}

/** Suggested questions for the screen the chat was opened from: (button label, question sent). */
private fun suggestionsFor(context: AiScreenContext?): List<Pair<Int, Int>> = when (context) {
    AiScreenContext.NUTRITION -> listOf(R.string.ai_suggest_nutrition to R.string.ai_suggest_nutrition_prompt)
    AiScreenContext.WORKOUT, AiScreenContext.EXERCISE_LIBRARY -> listOf(R.string.ai_suggest_workout to R.string.ai_suggest_workout_prompt)
    AiScreenContext.EXERCISE_DETAIL -> listOf(R.string.ai_suggest_technique to R.string.ai_suggest_technique_prompt)
    else -> listOf(R.string.ai_suggest_progress to R.string.ai_suggest_progress_prompt)
}

@Composable
private fun Suggestions(vm: AiChatViewModel) {
    val items = suggestionsFor(vm.context)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { (label, prompt) ->
            val text = stringResource(prompt)
            FitnexSecondaryButton(stringResource(label), { vm.send(text) }, Modifier.fillMaxWidth().testTag("ai_suggestion"))
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val colors = FitnexTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            if (m.fromUser) AnnotatedString(m.text) else lightMarkdown(m.text), style = FitnexTheme.typography.bodyMedium,
            color = if (m.fromUser) colors.onAccent else colors.textPrimary,
            modifier = Modifier.widthIn(max = 320.dp)
                .background(if (m.fromUser) colors.accent else colors.surfaceRaised, RoundedCornerShape(18.dp))
                .padding(Spacing.md).testTag(if (m.fromUser) "ai_msg_user" else "ai_msg_model"),
        )
    }
}

/** Gemini answers with light Markdown: **bold** becomes bold, "* " / "- " bullets become "• ", "#" headings lose their marks. */
internal fun lightMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        var line = raw.replace(Regex("^#{1,6}\\s+"), "")
        line = line.replace(Regex("^(\\s*)[*-]\\s+"), "$1• ")
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            // Odd segments sit between a pair of ** markers; an unmatched trailing ** stays plain text.
            if (i % 2 == 1 && i < parts.size - 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            else { if (i % 2 == 1) append("**"); append(part) }
        }
    }
}

@Composable
private fun Disclaimer() {
    Text(stringResource(R.string.ai_disclaimer), style = FitnexTheme.typography.bodySmall, color = FitnexTheme.colors.textSecondary, modifier = Modifier.padding(Spacing.gutter))
}

private fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NO_CONNECTION -> R.string.ai_error_no_connection
    AiFailure.RATE_LIMITED -> R.string.ai_error_rate_limited
    AiFailure.NOT_CONFIGURED -> R.string.ai_error_not_configured
    AiFailure.NOT_SIGNED_IN -> R.string.ai_error_signed_out
    AiFailure.NO_CONSENT, AiFailure.UNAVAILABLE -> R.string.ai_error_unavailable
}
