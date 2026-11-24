package com.fitnex.core.testing

import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.data.sync.CloudSyncState
import com.fitnex.core.data.sync.SyncProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cloud sync double: records calls; [eraseSucceeds] simulates an unreachable server. */
class FakeCloudSync : CloudSync {
    override val restoringUserId = MutableStateFlow<String?>(null)
    val state = MutableStateFlow(CloudSyncState())
    var eraseSucceeds = true
    val erased = mutableListOf<String>()
    val reuploaded = mutableListOf<String>()
    var syncCalls = 0
    var exportContent = "{}"

    override fun state(userId: String): Flow<CloudSyncState> = state
    override suspend fun syncNow(userId: String): SyncProblem? { syncCalls++; return null }
    override suspend fun eraseCloudCopy(userId: String): Boolean = eraseSucceeds.also { if (it) erased += userId }
    override suspend fun reuploadAll(userId: String) { reuploaded += userId }
    override suspend fun exportJson(userId: String): String = exportContent
    override fun start() = Unit
}

/** Keeps the StateFlow type visible for callers that only need restoring. */
val FakeCloudSync.restoring: StateFlow<String?> get() = restoringUserId
