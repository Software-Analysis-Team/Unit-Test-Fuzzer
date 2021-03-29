package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import java.nio.file.Files
import java.nio.file.Paths

class EvosuiteGenerator(private val evoSuiteJarLocation: String, private val cp: String) : TestGenerator {

    override fun getTests(testClassName: String, outputDir: String, timeout: Int): List<String> {
        val tests: MutableList<String> = mutableListOf()

        val defaultCommand =
            "java -jar $evoSuiteJarLocation -class $testClassName -projectCP $cp -Dsearch_budget=$timeout"

        CommandExecutor.execute(defaultCommand, outputDir)
        val generatedTestsDir = Paths.get(outputDir, "evosuite-tests")
        Files.walk(generatedTestsDir).forEach {
            if (Files.isRegularFile(it)) {
                tests.add(it.toFile().readText())
            }
        }
        generatedTestsDir.toFile().deleteRecursively()

        return tests
    }
}