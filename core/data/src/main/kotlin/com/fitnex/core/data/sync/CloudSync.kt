package com.fitnex.core.data.sync

import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.common.result.AppError
import com.fitnex.core.common.result.AppResult
import com.fitnex.core.database.dao.UserDao
import com.fitnex.core.database.sync.SyncDao
import com.fitnex.core.database.sync.SyncRecord
import com.fitnex.core.database.sync.SyncStateEntity
import com.fitnex.core.database.sync.SyncStore
import com.fitnex.core.network.BuildConfig
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.NetworkMonitor
import com.fitnex.core.network.SyncPullRequest
import com.fitnex.core.network.SyncPushRequest
import com.fitnex.core.network.SyncRecordDto
import com.fitnex.core.network.safeApiCall
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Why the last sync did not complete. */
enum class SyncProblem { NOT_CONFIGURED, OFFLINE, SESSION_EXPIRED, RATE_LIMITED, SERVER }

data class CloudSyncState(
    val lastSyncedAt: Instant? = null,
    val pendingChanges: Int = 0,
    val running: Boolean = false,
    val problem: SyncProblem? = null,
)

/** Cloud copy of the user's data (spec §10, Phase 9). Room stays the source of truth; this mirrors it. */
interface CloudSync {
    /** The user whose data is being restored right after signing in on a device that never synced it. */
    val restoringUserId: StateFlow<String?>

    fun state(userId: String): Flow<CloudSyncState>

    /** Push local changes, then pull everything newer. Returns null on success. */
    suspend fun syncNow(userId: String): SyncProblem?

    /** Deletes the cloud copy (account deletion). True when nothing remains in the cloud. */
    suspend fun eraseCloudCopy(userId: String): Boolean

    /** The cloud copy was erased but the account survived: upload everything again on the next sync. */
    suspend fun reuploadAll(userId: String)

    /** All of the user's data on this device as pretty JSON ("Exportar mis datos"). */
    suspend fun exportJson(userId: String): String

    /** Starts the automatic triggers (sign-in, connectivity back, local edits, every 15 min while open). */
    fun start()
}

@Singleton
class BackendCloudSync @Inject constructor(
    private val api: FitnexBackendApi,
    private val store: SyncStore,
    private val syncDao: SyncDao,
    private val userDao: UserDao,
    private val network: NetworkMonitor,
    private val auth: AuthRepository,
    private val clock: Clock,
) : CloudSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val running = MutableStateFlow(false)
    private val lastProblem = MutableStateFlow<SyncProblem?>(null)
    private val _restoring = MutableStateFlow<String?>(null)
    override val restoringUserId: StateFlow<String?> = _restoring.asStateFlow()
    private var started = false

    private fun configured() = !BuildConfig.BACKEND_URL.contains(".invalid")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun state(userId: String): Flow<CloudSyncState> =
        combine(
            syncDao.observeState(userId),
            store.localChanges().onStart { emit(emptySet()) }.map { withContext(Dispatchers.IO) { pendingAll(userId) } },
            running,
            lastProblem,
        ) { saved, pending, isRunning, problem ->
            CloudSyncState(saved?.lastSyncedAt, pending, isRunning, problem)
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun start() {
        if (started) return
        started = true
        scope.launch {
            auth.currentUser.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                if (uid == null) return@collectLatest
                restoreIfNeeded(uid)
                launch { network.isOnline.filter { it }.collect { syncNow(uid) } } // also covers app start
                launch {
                    store.localChanges().debounce(LOCAL_EDIT_DEBOUNCE_MS).collect {
                        if (withContext(Dispatchers.IO) { pendingAll(uid) } > 0) syncNow(uid)
                    }
                }
                while (true) {
                    delay(PERIODIC_MS)
                    syncNow(uid)
                }
            }
        }
    }

    /** First sign-in on this device without a local profile: bring the cloud copy down before onboarding. */
    private suspend fun restoreIfNeeded(uid: String) {
        if (syncDao.state(uid) != null || userDao.get(uid) != null) return
        _restoring.value = uid
        try {
            withTimeoutOrNull(RESTORE_TIMEOUT_MS) { syncNow(uid) }
        } finally {
            _restoring.value = null
        }
    }

    override suspend fun syncNow(userId: String): SyncProblem? = mutex.withLock {
        if (!configured()) return@withLock SyncProblem.NOT_CONFIGURED.also { lastProblem.value = it }
        running.value = true
        try {
            val problem = withContext(Dispatchers.IO) { push(userId) ?: pull(userId) }
            lastProblem.value = problem
            problem
        } finally {
            running.value = false
        }
    }

    /** Pending changes of the account and of its "Entrenar juntos" profiles. */
    private fun pendingAll(accountId: String): Int = store.scopeIds(accountId).sumOf { store.pendingCount(it) }

    private suspend fun push(accountId: String): SyncProblem? {
        // Every profile's rows travel under the account's own token (the server scopes by account).
        for (owner in store.scopeIds(accountId)) {
            for (i in 0 until MAX_PUSH_BATCHES) {
                val batch = store.pendingChanges(owner, PUSH_BATCH).limitBySize(PUSH_MAX_CHARS)
                if (batch.isEmpty()) break
                when (val r = safeApiCall { api.syncPush(SyncPushRequest(batch.map { it.toDto() })) }) {
                    is AppResult.Success -> store.markPushed(batch)
                    is AppResult.Failure -> return r.error.toProblem()
                }
            }
        }
        return null
    }

    private suspend fun pull(userId: String): SyncProblem? {
        var cursor = syncDao.state(userId)?.cursor ?: 0L
        val received = mutableListOf<SyncRecord>()
        // Collect every page first: a child may arrive in an earlier page than its (re-edited) parent.
        do {
            val page = when (val r = safeApiCall { api.syncPull(SyncPullRequest(since = cursor, limit = PULL_PAGE)) }) {
                is AppResult.Success -> r.data
                is AppResult.Failure -> return r.error.toProblem()
            }
            received += page.records.map { it.toRecord() }
            cursor = maxOf(cursor, page.next)
        } while (page.hasMore && received.size < MAX_PULL_RECORDS)
        if (received.isNotEmpty()) store.applyRemote(received)
        syncDao.saveState(SyncStateEntity(userId, cursor, clock.instant()))
        return null
    }

    override suspend fun eraseCloudCopy(userId: String): Boolean {
        if (!configured()) return true
        return safeApiCall { api.syncErase() } is AppResult.Success
    }

    override suspend fun reuploadAll(userId: String) = withContext(Dispatchers.IO) { store.scopeIds(userId).forEach(store::markAllPending) }

    override suspend fun exportJson(userId: String): String = withContext(Dispatchers.IO) {
        val doc = buildJsonObject {
            put("format", "fitnex-export")
            put("formatVersion", 1)
            put("exportedAt", clock.instant().toString())
            put("userId", userId)
            put("notes", "Fechas en milisegundos UTC (instantes) o días desde 1970-01-01 (fechas). Nutrientes y kcal son estimaciones.")
            put("data", store.export(userId))
        }
        prettyJson.encodeToString(JsonObject.serializer(), doc)
    }

    private fun AppError.toProblem() = when (this) {
        AppError.NoConnection -> SyncProblem.OFFLINE
        AppError.Unauthorized -> SyncProblem.SESSION_EXPIRED
        is AppError.RateLimited -> SyncProblem.RATE_LIMITED
        else -> SyncProblem.SERVER
    }

    private companion object {
        const val PUSH_BATCH = 200 // = server SYNC_LIMITS.pushRecords
        const val PUSH_MAX_CHARS = 900_000 // half the server's 1.8 MB cap: room for JSON overhead
        const val MAX_PUSH_BATCHES = 50
        const val PULL_PAGE = 500
        const val MAX_PULL_RECORDS = 50_000
        const val LOCAL_EDIT_DEBOUNCE_MS = 5_000L
        const val PERIODIC_MS = 15 * 60_000L
        const val RESTORE_TIMEOUT_MS = 25_000L
        val prettyJson = Json { prettyPrint = true }
    }
}

/** Keeps whole records while their JSON stays under [maxChars] (always at least one). */
internal fun List<SyncRecord>.limitBySize(maxChars: Int): List<SyncRecord> {
    var total = 0
    return takeWhile { r ->
        total += (r.data?.toString()?.length ?: 0) + r.id.length + 64
        total <= maxChars
    }.ifEmpty { take(1) }
}

internal fun SyncRecord.toDto() = SyncRecordDto(t = table, id = id, u = updatedAt, d = deleted, data = data)
internal fun SyncRecordDto.toRecord() = SyncRecord(table = t, id = id, updatedAt = u, deleted = d, data = data)
