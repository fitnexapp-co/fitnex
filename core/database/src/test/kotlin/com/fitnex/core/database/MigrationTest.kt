package com.fitnex.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    // Absolute path: Room 2.8's helper compares the configured name with the resolved path,
    // so a bare name fails under Robolectric ("configured to open 'x' but '/…/x' was requested").
    private val dbName: String = InstrumentationRegistry.getInstrumentation().targetContext
        .getDatabasePath("migration-test").absolutePath

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FitnexDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsUsersAndMarksExistingOnboardingAsDone() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO users (id, name, email, birth_date, height_cm, optional_weight_kg, experience,
                    activity_level, available_days, session_minutes, equipment, dietary_preferences, allergies,
                    ai_consent_at, created_at, updated_at, sync_status, deleted_at)
                VALUES ('u1', 'Ana', 'ana@example.com', 7000, 170.0, NULL, 'BEGINNER', NULL, 'MONDAY', 45,
                    'DUMBBELL', '[]', '[]', NULL, 1700000000000, 1700000000000, 'SYNCED', NULL)
                """.trimIndent(),
            )
            close()
        }

        // Validates the migrated schema against the exported 2.json (columns, types, indices).
        val db = helper.runMigrationsAndValidate(dbName, 2, true, FitnexMigrations.MIGRATION_1_2)

        db.query("SELECT name, onboarding_completed_at, height_cm FROM users WHERE id = 'u1'").use { c ->
            c.moveToFirst()
            assertEquals("Ana", c.getString(0))
            assertEquals(1700000000000L, c.getLong(1))
            assertEquals(170.0, c.getDouble(2), 0.001)
        }
    }

    @Test
    fun migrate2To3KeepsSessionsAndSetsWithNullPlan() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO workout_sessions (id, user_id, workout_id, workout_name_snapshot, started_at, finished_at,
                    duration_seconds, paused_seconds, status, current_exercise_index, updated_at, sync_status, deleted_at)
                VALUES ('s1', 'u1', NULL, 'Pierna', 1700000000000, 1700003600000, 3600, 0, 'FINISHED', 2,
                    1700003600000, 'SYNCED', NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO exercise_sets (id, session_id, exercise_id, exercise_name_snapshot, exercise_position, set_number,
                    repetitions, weight_kg, rest_seconds, source, completed_at, updated_at, sync_status, deleted_at)
                VALUES ('x1', 's1', 'back_squat', 'Sentadilla', 0, 1, 8, 60.0, 90, 'MANUAL', 1700000100000,
                    1700000100000, 'SYNCED', NULL)
                """.trimIndent(),
            )
            close()
        }

        // Full chain from v2, validated against 3.json.
        val db = helper.runMigrationsAndValidate(dbName, 3, true, *FitnexMigrations.ALL)

        db.query("SELECT workout_name_snapshot, plan_json, paused_at, current_exercise_index FROM workout_sessions").use { c ->
            c.moveToFirst()
            assertEquals("Pierna", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
            assertEquals(2, c.getInt(3))
        }
        db.query("SELECT repetitions, weight_kg FROM exercise_sets WHERE id = 'x1'").use { c ->
            c.moveToFirst()
            assertEquals(8, c.getInt(0))
            assertEquals(60.0, c.getDouble(1), 0.001)
        }
    }

    @Test
    fun migrate3To4KeepsMealsAndAddsCatalogLinkAndRecipes() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """
                INSERT INTO meals (id, user_id, meal_type, date, logged_at, description, image_reference, analysis_status,
                    updated_at, sync_status, deleted_at)
                VALUES ('m1', 'u1', 'BREAKFAST', '2026-11-19', 1700000000000, NULL, NULL, 'MANUAL', 1700000000000, 'SYNCED', NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO food_items (id, meal_id, name, portion_description, portion_grams, protein_g, carbs_g, fat_g, fiber_g,
                    kcal_estimate, micronutrients_json, confidence, user_edited, updated_at, sync_status, deleted_at)
                VALUES ('f1', 'm1', 'Avena', '40 g', 40.0, 6.8, 26.5, 2.6, 4.0, 150.0, NULL, NULL, 1, 1700000000000, 'SYNCED', NULL)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, *FitnexMigrations.ALL)

        db.query("SELECT name, food_id, protein_g FROM food_items WHERE id = 'f1'").use { c ->
            c.moveToFirst()
            assertEquals("Avena", c.getString(0))
            assertTrue("existing entries have no catalog link", c.isNull(1))
            assertEquals(6.8, c.getDouble(2), 0.001)
        }
        db.execSQL("PRAGMA foreign_keys = ON") // Room enables this at runtime; the raw helper database does not.
        db.execSQL("INSERT INTO recipes (id, user_id, name, servings, created_at, updated_at, sync_status, deleted_at) VALUES ('r1', 'u1', 'Gachas', 2, 1, 1, 'PENDING_UPSERT', NULL)")
        db.execSQL("INSERT INTO recipe_items (id, recipe_id, food_id, grams, position) VALUES ('i1', 'r1', 'oats', 80.0, 0)")
        db.execSQL("DELETE FROM recipes WHERE id = 'r1'")
        db.query("SELECT COUNT(*) FROM recipe_items").use { c -> c.moveToFirst(); assertEquals("items cascade with their recipe", 0, c.getInt(0)) }
    }

    @Test
    fun migrate4To5AddsSyncTablesAndTombstoneTriggers() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO water_logs (id, user_id, quantity_ml, timestamp, updated_at, sync_status, deleted_at) VALUES ('w1', 'u1', 250, 1, 1, 'SYNCED', NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, *FitnexMigrations.ALL)

        db.query("SELECT COUNT(*) FROM water_logs").use { c -> c.moveToFirst(); assertEquals("existing data kept", 1, c.getInt(0)) }
        db.execSQL("DELETE FROM water_logs WHERE id = 'w1'")
        db.query("SELECT table_name, row_id, user_id FROM sync_tombstones").use { c ->
            assertTrue("a hard delete leaves a tombstone", c.moveToFirst())
            assertEquals("water_logs", c.getString(0))
            assertEquals("w1", c.getString(1))
            assertEquals("u1", c.getString(2))
        }
    }

    @Test
    fun migrate6To7KeepsExercisesAndSetsAndAddsTimedFields() {
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                """
                INSERT INTO exercises (id, name, description, instructions, technique_tips, common_mistakes, equipment, type,
                    difficulty, media_uri, camera_rep_supported, owner_user_id, updated_at, sync_status, deleted_at)
                VALUES ('my_row', 'Remo con toalla', '', '[]', '[]', '[]', 'OTHER', 'STRENGTH', 'BEGINNER', NULL, 0, 'u1', 1, 'SYNCED', NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO exercise_sets (id, session_id, exercise_id, exercise_name_snapshot, exercise_position, set_number,
                    repetitions, weight_kg, rest_seconds, source, completed_at, updated_at, sync_status, deleted_at)
                VALUES ('x1', 's1', 'bench_press', 'Press de banca', 0, 1, 8, 60.0, 90, 'MANUAL', 1, 1, 'SYNCED', NULL)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, *FitnexMigrations.ALL)

        db.query("SELECT name, tracking, search_terms, owner_user_id FROM exercises WHERE id = 'my_row'").use { c ->
            c.moveToFirst()
            assertEquals("Remo con toalla", c.getString(0))
            assertEquals("existing exercises default to weight × reps", "WEIGHT_REPS", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("u1", c.getString(3))
        }
        db.query("SELECT repetitions, weight_kg, duration_seconds, distance_m FROM exercise_sets WHERE id = 'x1'").use { c ->
            c.moveToFirst()
            assertEquals(8, c.getInt(0))
            assertEquals(60.0, c.getDouble(1), 0.001)
            assertTrue("old sets have no time", c.isNull(2))
            assertTrue("old sets have no distance", c.isNull(3))
        }
    }

    @Test
    fun migrate7To8AddsTheMealPlanWithItsTombstoneTrigger() {
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO recipes (id, user_id, name, servings, created_at, updated_at, sync_status, deleted_at) VALUES ('r1', 'u1', 'Gachas', 2, 1, 1, 'SYNCED', NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, *FitnexMigrations.ALL)

        db.query("SELECT COUNT(*) FROM recipes").use { c -> c.moveToFirst(); assertEquals("existing data kept", 1, c.getInt(0)) }
        db.execSQL(
            "INSERT INTO meal_plan_items (id, user_id, day_of_week, meal_type, food_id, recipe_id, name, grams, servings, portion, position, " +
                "created_at, updated_at, sync_status, deleted_at) VALUES ('p1', 'u1', 1, 'BREAKFAST', 'banana', NULL, 'Plátano', 118.0, NULL, '1 unidad', 0, 1, 1, 'PENDING_UPSERT', NULL)",
        )
        db.execSQL("DELETE FROM meal_plan_items WHERE id = 'p1'")
        db.query("SELECT table_name, row_id, user_id FROM sync_tombstones WHERE table_name = 'meal_plan_items'").use { c ->
            assertTrue("a hard delete leaves a tombstone", c.moveToFirst())
            assertEquals("p1", c.getString(1))
            assertEquals("u1", c.getString(2))
        }
    }

    @Test
    fun migrate8To9AddsBodyMeasurementsAndKeepsThePlan() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO meal_plan_items (id, user_id, day_of_week, meal_type, food_id, recipe_id, name, grams, servings, portion, position, " +
                    "created_at, updated_at, sync_status, deleted_at) VALUES ('p1', 'u1', 1, 'BREAKFAST', 'banana', NULL, 'Plátano', 118.0, NULL, '1 unidad', 0, 1, 1, 'SYNCED', NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 9, true, *FitnexMigrations.ALL)

        db.query("SELECT COUNT(*) FROM meal_plan_items").use { c -> c.moveToFirst(); assertEquals("plan kept", 1, c.getInt(0)) }
        db.execSQL("INSERT INTO body_measurements (id, user_id, date, waist_cm, chest_cm, hips_cm, arm_cm, thigh_cm, created_at, updated_at, sync_status, deleted_at) VALUES ('b1', 'u1', 20413, 80.0, NULL, NULL, 35.0, NULL, 1, 1, 'PENDING_UPSERT', NULL)")
        db.execSQL("DELETE FROM body_measurements WHERE id = 'b1'")
        db.query("SELECT user_id FROM sync_tombstones WHERE table_name = 'body_measurements' AND row_id = 'b1'").use { c ->
            assertTrue("a hard delete leaves a tombstone", c.moveToFirst())
            assertEquals("u1", c.getString(0))
        }
    }
}