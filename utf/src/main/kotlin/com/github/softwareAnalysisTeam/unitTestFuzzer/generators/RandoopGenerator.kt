package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.JarExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import com.intellij.util.io.delete
import com.intellij.util.io.isFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class RandoopGenerator(
    private val javaHome: String,
    private val buildLocation: String,
    private val randoopJarLocation: String
) : TestGenerator {

    override fun getTests(testClassName: String, classPath: String): List<String> {
        val tests: MutableList<String> = mutableListOf()
        val generatedTestsDir = Files.createDirectory(Paths.get(classPath, "generatedTests"))
        val defaultCommand: String =
            javaHome + File.separator + "bin" + File.separator + "java" + " " + "-classpath" + " " + buildLocation + File.pathSeparator + randoopJarLocation + " " + "randoop.main.Main gentests" +
                    " " + "--testclass=" + testClassName + " " + "--time-limit=5"

        JarExecutor.execute(defaultCommand, generatedTestsDir.toString())

        Files.walk(generatedTestsDir).forEach {
            if (it.isFile()) {
                tests.add(it.toFile().readText())
            }
        }
        generatedTestsDir.delete()

        return tests
    }
}