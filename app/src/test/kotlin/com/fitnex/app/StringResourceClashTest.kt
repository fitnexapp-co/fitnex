package com.fitnex.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android merges every module's strings into one table: two modules using the same name with
 * different text means one screen silently shows the other's text ("Alimento personalizado" on the
 * create-exercise sheet). Same name + same text is harmless.
 */
class StringResourceClashTest {
    @Test
    fun noTwoModulesUseTheSameStringNameWithDifferentText() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: error("project root not found")
        val nameRx = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val seen = mutableMapOf<String, MutableMap<String, String>>() // name -> module -> text
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "node_modules" }
            .filter { it.name == "strings.xml" && it.parentFile.name == "values" && it.path.contains("src${File.separator}main") }
            .forEach { f ->
                val module = f.relativeTo(root).path.substringBefore("${File.separator}src")
                nameRx.findAll(f.readText()).forEach { m -> seen.getOrPut(m.groupValues[1]) { mutableMapOf() }[module] = m.groupValues[2].trim() }
            }
        val clashes = seen.filterValues { it.values.toSet().size > 1 }
        assertTrue("Nombres de texto repetidos con distinto contenido: $clashes", clashes.isEmpty())
    }
}