package com.fitnex.feature.workout.camera

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The catalogue may only promise the camera counter for exercises that really have a detector. */
class CameraCatalogConsistencyTest {
    @Test
    fun catalogueCameraFlagsMatchDetectors() {
        val json = File("../../core/data/src/main/assets/catalog/catalog.json").readText()
        val flagged = Regex(""""id":\s*"([a-z_]+)"[^\n]*?"camera":\s*true""").findAll(json).map { it.groupValues[1] }.toSet()
        assertEquals(CameraExercises.all.map { it.exerciseId }.toSet(), flagged)
    }
}
