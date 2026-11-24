package com.fitnex.core.database.sync

/**
 * Which tables travel to the cloud and how (spec §10, Phase 9).
 *
 * Every synced table embeds SyncMeta (`updated_at`, `sync_status`, `deleted_at`), so the engine can
 * work on raw rows generically: a row is pending while `sync_status != 'SYNCED'`; conflicts are
 * resolved last-writer-wins on `updated_at` (deletions win ties, as on the server).
 *
 * Not synced on purpose:
 *  - the built-in exercise / muscle catalogue (shipped in the APK; custom exercises DO sync);
 *  - FITNEX AI conversations (they stay on the device; they are included in the data export);
 *  - meal photo files (only their local reference travels; another device shows the meal without it).
 */
object SyncSchema {
    /** Children always rewritten together with their parent: they travel inside it. */
    data class Child(val table: String, val parentColumn: String, val orderBy: String)

    data class Table(
        val name: String,
        /** WHERE fragment selecting this user's rows; exactly one `?` (the user id). */
        val scope: String,
        /** SQL expression (on OLD.*) giving the owner in the delete trigger. */
        val ownerOfDeleted: String = "OLD.user_id",
        val keyColumns: List<String> = listOf("id"),
        val child: Child? = null,
        /** Nullable references to other synced tables: nulled on apply when the target is not here. */
        val optionalRefs: Map<String, String> = emptyMap(),
    ) {
        /** Same composite key format on the device, in triggers and on the server. */
        val keyOfDeleted: String = keyColumns.joinToString(" || '|' || ") { "OLD.`$it`" }
    }

    /** Apply order respects foreign keys: parents before children. */
    val tables: List<Table> = listOf(
        Table("users", scope = "id = ?", ownerOfDeleted = "OLD.id"),
        // Only exercises the user created (catalogue rows have no owner); their muscles travel inside.
        Table(
            "exercises", scope = "owner_user_id = ?", ownerOfDeleted = "OLD.owner_user_id",
            child = Child("exercise_muscles", "exercise_id", "muscle_id"),
        ),
        Table("workouts", scope = "user_id = ?", child = Child("workout_exercises", "workout_id", "position")),
        Table("workout_sessions", scope = "user_id = ?", optionalRefs = mapOf("workout_id" to "workouts")),
        Table(
            "exercise_sets",
            scope = "session_id IN (SELECT id FROM workout_sessions WHERE user_id = ?)",
            ownerOfDeleted = "(SELECT user_id FROM workout_sessions WHERE id = OLD.session_id)",
        ),
        Table("scheduled_sessions", scope = "user_id = ?", optionalRefs = mapOf("workout_id" to "workouts")),
        Table("planned_rest_days", scope = "user_id = ?"),
        Table("meals", scope = "user_id = ?"),
        Table(
            "food_items",
            scope = "meal_id IN (SELECT id FROM meals WHERE user_id = ?)",
            ownerOfDeleted = "(SELECT user_id FROM meals WHERE id = OLD.meal_id)",
        ),
        Table("water_logs", scope = "user_id = ?"),
        Table("recipes", scope = "user_id = ?", child = Child("recipe_items", "recipe_id", "position")),
        Table("meal_plan_items", scope = "user_id = ?", optionalRefs = mapOf("recipe_id" to "recipes")),
        Table("body_measurements", scope = "user_id = ?"),
        Table("favorite_exercises", scope = "user_id = ?", keyColumns = listOf("user_id", "exercise_id")),
    )

    val byName: Map<String, Table> = tables.associateBy { it.name }

    /** Everything the user owns, for "Exportar mis datos" (synced tables, their children and local-only data). */
    val exportScopes: List<Pair<String, String>> =
        tables.flatMap { t ->
            listOfNotNull(
                t.name to t.scope,
                t.child?.let { c -> c.table to "${c.parentColumn} IN (SELECT id FROM ${t.name} WHERE ${t.scope})" },
            )
        } + listOf(
            "ai_conversations" to "user_id = ?",
            "ai_messages" to "conversation_id IN (SELECT id FROM ai_conversations WHERE user_id = ?)",
        )

    private const val NOW_MILLIS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /**
     * One AFTER DELETE trigger per synced table: any hard delete — a DAO, a cascade, a future feature —
     * leaves a tombstone, unless the delete is itself being applied from the cloud (lock row present).
     */
    val triggers: List<String> = tables.map { t ->
        """
        CREATE TRIGGER IF NOT EXISTS `sync_tombstone_${t.name}` AFTER DELETE ON `${t.name}`
        WHEN NOT EXISTS (SELECT 1 FROM sync_apply_lock)
        BEGIN
            INSERT OR REPLACE INTO sync_tombstones (table_name, row_id, user_id, deleted_at)
            VALUES ('${t.name}', ${t.keyOfDeleted}, ${t.ownerOfDeleted}, $NOW_MILLIS);
        END
        """.trimIndent()
    }

    /** Synced tables created after schema v7: older migrations must not create their triggers. */
    private val addedAfterV7 = setOf("meal_plan_items", "body_measurements")

    /** The tombstone trigger of one table (for the migration that creates that table). */
    fun triggerFor(table: String): String = triggers[tables.indexOfFirst { it.name == table }.also { require(it >= 0) { table } }]

    /** Triggers for the tables that exist up to schema v7 (used by the v5 and v6 migrations). */
    val triggersUpToV7: List<String> = tables.zip(triggers).filter { (t, _) -> t.name !in addedAfterV7 }.map { it.second }

    /** Tables added in schema v5 (exact Room DDL, verified by MigrationTest). */
    val createTables: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `sync_tombstones` (`table_name` TEXT NOT NULL, `row_id` TEXT NOT NULL, " +
            "`user_id` TEXT, `deleted_at` INTEGER NOT NULL, PRIMARY KEY(`table_name`, `row_id`))",
        "CREATE TABLE IF NOT EXISTS `sync_state` (`user_id` TEXT NOT NULL, `cursor` INTEGER NOT NULL, " +
            "`last_synced_at` INTEGER, PRIMARY KEY(`user_id`))",
        "CREATE TABLE IF NOT EXISTS `sync_apply_lock` (`id` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}
