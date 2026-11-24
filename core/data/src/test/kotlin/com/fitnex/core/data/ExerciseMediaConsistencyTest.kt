package com.fitnex.core.data

import com.fitnex.core.data.catalog.CatalogSeeder
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every photo the catalogue points to must exist in the assets (own photos and reference ones). */
class ExerciseMediaConsistencyTest {
    private val assets = File("src/main/assets")
    private val catalog = CatalogSeeder.parse(File(assets, "catalog/catalog.json").readText())

    @Test
    fun everyLinkedPhotoExists() {
        val missing = catalog.exercises.flatMap { e ->
            val folder = when {
                e.media -> e.id
                e.mediaFrom != null -> e.mediaFrom
                else -> null
            } ?: return@flatMap emptyList()
            listOf("0.jpg", "1.jpg").filterNot { File(assets, "exercise_media/$folder/$it").isFile }.map { "${e.id} → $folder/$it" }
        }
        assertTrue("Missing photos: $missing", missing.isEmpty())
    }

    @Test
    fun quickVersionCheckMatchesTheParsedCatalogue() {
        val text = File(assets, "catalog/catalog.json").readText()
        org.junit.Assert.assertEquals(catalog.version, CatalogSeeder.peekVersion(text))
    }

    @Test
    fun ownPhotoAndReferenceAreNeverBothSet() {
        val both = catalog.exercises.filter { it.media && it.mediaFrom != null }.map { it.id }
        assertTrue("Both media and mediaFrom: $both", both.isEmpty())
    }
}
