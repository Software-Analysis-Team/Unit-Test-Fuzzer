package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.JarExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import com.intellij.util.io.delete
import com.intellij.util.io.isFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class EvosuiteGenerator(private val javaHome: String, private val evoSuiteJarLocation: String) : TestGenerator {

    override fun getTests(testClassName: String, projectCP: String): List<String> {
        val tests: MutableList<String> = mutableListOf()

        val defaultCommand: String =
            javaHome + File.separator + "bin" + File.separator + "java" + " -jar " + evoSuiteJarLocation + " " + "-class" + " " + testClassName + " " + "-projectCP" + " " + projectCP

        JarExecutor.execute(defaultCommand, projectCP)
        val generatedTestsDir = Paths.get(projectCP, "evosuite-tests")
        Files.walk(generatedTestsDir).forEach {
            if (it.isFile()) {
                tests.add(it.toFile().readText())
            }
        }
        generatedTestsDir.delete()

        return tests
    }
}