package com.fitnex.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitnex.core.database.converter.Converters
import com.fitnex.core.database.dao.AccountDao
import com.fitnex.core.database.dao.AiDao
import com.fitnex.core.database.dao.CalendarDao
import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.dao.NutritionDao
import com.fitnex.core.database.dao.SessionDao
import com.fitnex.core.database.dao.UserDao
import com.fitnex.core.database.dao.WorkoutDao
import com.fitnex.core.database.entity.AiConversationEntity
import com.fitnex.core.database.entity.AiMessageEntity
import com.fitnex.core.database.entity.ExerciseEntity
import com.fitnex.core.database.entity.ExerciseMuscleEntity
import com.fitnex.core.database.entity.ExerciseSetEntity
import com.fitnex.core.database.entity.FavoriteExerciseEntity
import com.fitnex.core.database.entity.FoodItemEntity
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.MuscleEntity
import com.fitnex.core.database.entity.PlannedRestDayEntity
import com.fitnex.core.database.entity.ScheduledSessionEntity
import com.fitnex.core.database.entity.UserEntity
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutExerciseEntity
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.database.sync.SyncApplyLockEntity
import com.fitnex.core.database.sync.SyncDao
import com.fitnex.core.database.sync.SyncSchema
import com.fitnex.core.database.sync.SyncStateEntity
import com.fitnex.core.database.sync.SyncTombstoneEntity

@Database(
    version = FitnexDatabase.VERSION,
    exportSchema = true,
    entities = [
        UserEntity::class,
        MuscleEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        FavoriteExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSessionEntity::class,
        ExerciseSetEntity::class,
        ScheduledSessionEntity::class,
        PlannedRestDayEntity::class,
        MealEntity::class,
        FoodItemEntity::class,
        WaterLogEntity::class,
        com.fitnex.core.database.entity.RecipeEntity::class,
        com.fitnex.core.database.entity.RecipeItemEntity::class,
        com.fitnex.core.database.entity.MealPlanItemEntity::class,
        com.fitnex.core.database.dao.BodyMeasurementEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
        SyncTombstoneEntity::class,
        SyncStateEntity::class,
        SyncApplyLockEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class FitnexDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun catalogDao(): CatalogDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun calendarDao(): CalendarDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun bodyDao(): com.fitnex.core.database.dao.BodyDao
    abstract fun aiDao(): AiDao
    abstract fun accountDao(): AccountDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "fitnex.db"
        const val VERSION = 9

        /** Fresh installs get the tombstone triggers too (migrations only cover upgrades). */
        val syncCallback: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SyncSchema.triggers.forEach(db::execSQL)
            }
        }
    }
}

/**
 * Every schema change bumps [FitnexDatabase.VERSION] and adds a Migration here (or an
 * @AutoMigration). Destructive fallback is deliberately NOT enabled: user training history
 * must never be wiped by an app update. Each migration has a test in MigrationTest.
 */
object FitnexMigrations {
    /** v1 → v2: track onboarding completion per user. Existing rows count as completed. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `users` ADD COLUMN `onboarding_completed_at` INTEGER")
            db.execSQL("UPDATE `users` SET `onboarding_completed_at` = `created_at`")
        }
    }

    /** v2 → v3: plan snapshot and pause timestamp for resumable live sessions. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `plan_json` TEXT")
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `paused_at` INTEGER")
        }
    }

    /** v3 → v4: logged foods link to the catalog; user recipes. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `food_id` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`servings` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_user_id` ON `recipes` (`user_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipe_items` (`id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `food_id` TEXT NOT NULL, " +
                    "`grams` REAL NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_items_recipe_id` ON `recipe_items` (`recipe_id`)")
        }
    }

    /** v4 → v5: cloud sync (tombstones written by triggers, per-user cursor, apply lock). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SyncSchema.createTables.forEach(db::execSQL)
            SyncSchema.triggersUpToV7.forEach(db::execSQL)
        }
    }

    /** v5 → v6: custom exercises join the cloud sync (their tombstone trigger). No table changes. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SyncSchema.triggersUpToV7.forEach(db::execSQL) // IF NOT EXISTS: only the new one is created
            // Custom exercises created before sync existed: upload them on the next sync.
            db.execSQL("UPDATE exercises SET sync_status = 'PENDING_UPSERT' WHERE owner_user_id IS NOT NULL")
        }
    }

    /**
     * v6 → v7: expanded exercise catalogue. Exercises learn how they are logged and extra search
     * words; sets can store a duration and a distance. Columns are only added: nothing is lost.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `tracking` TEXT NOT NULL DEFAULT 'WEIGHT_REPS'")
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `search_terms` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `exercise_sets` ADD COLUMN `duration_seconds` INTEGER")
            db.execSQL("ALTER TABLE `exercise_sets` ADD COLUMN `distance_m` REAL")
        }
    }

    /** v7 → v8: weekly meal plan (synced, with its tombstone trigger). Only adds a table. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `meal_plan_items` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `day_of_week` INTEGER NOT NULL, " +
                    "`meal_type` TEXT NOT NULL, `food_id` TEXT, `recipe_id` TEXT, `name` TEXT NOT NULL, `grams` REAL, `servings` REAL, " +
                    "`portion` TEXT NOT NULL, `position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`sync_status` TEXT NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_items_user_id` ON `meal_plan_items` (`user_id`)")
            db.execSQL(com.fitnex.core.database.sync.SyncSchema.triggerFor("meal_plan_items"))
        }
    }

    /** v8 → v9: optional body measurements (adults; synced, with its tombstone trigger). Only adds a table. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `body_measurements` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                    "`waist_cm` REAL, `chest_cm` REAL, `hips_cm` REAL, `arm_cm` REAL, `thigh_cm` REAL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `sync_status` TEXT NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measurements_user_id_date` ON `body_measurements` (`user_id`, `date`)")
            db.execSQL(com.fitnex.core.database.sync.SyncSchema.triggerFor("body_measurements"))
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
}
