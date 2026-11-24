package com.fitnex.core.data.training

import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.database.dao.AccountDao
import com.fitnex.core.database.sync.SyncStore
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Reiniciar estadísticas": erases the training history (workouts, sets, records, minutes, streaks,
 * completed calendar days) of the account AND all its "Entrenar juntos" profiles, then syncs so the
 * cloud copy goes too. Routines, custom exercises, nutrition, weight and settings are kept.
 */
@Singleton
class TrainingResetRepository @Inject constructor(
    private val accounts: AccountDao,
    private val store: SyncStore,
    private val sync: CloudSync,
    private val clock: Clock,
) {
    /** Returns how many owners (account + profiles) were reset. */
    suspend fun resetAll(accountId: String): Int {
        val ids = withContext(Dispatchers.IO) { store.scopeIds(accountId) }
        accounts.resetTrainingHistory(ids, clock.instant())
        runCatching { sync.syncNow(accountId) } // best effort: pending deletions upload later anyway
        return ids.size
    }
}