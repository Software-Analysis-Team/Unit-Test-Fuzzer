package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import com.intellij.util.io.delete
import com.intellij.util.io.isFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class RandoopGenerator(
    private val javaHome: String,
    private val randoopJarLocation: String
) : TestGenerator {

    override fun getTests(testClassName: String, projectCP: String): List<String> {
        val tests: MutableList<String> = mutableListOf()
        val generatedTestsDir = Files.createDirectory(Paths.get(projectCP, "generatedTests"))
        val defaultCommand: String =
            javaHome + File.separator + "bin" + File.separator + "java" + " " + "-classpath" + " " + projectCP + File.pathSeparator + randoopJarLocation + " " + "randoop.main.Main gentests" +
                    " " + "--testclass=" + testClassName + " " + "--time-limit=5"

        CommandExecutor.execute(defaultCommand, generatedTestsDir.toString())

        Files.walk(generatedTestsDir).forEach {
            if (it.isFile()) {
                tests.add(it.toFile().readText())
            }
        }
        generatedTestsDir.delete()

        return tests
    }
}