package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import com.github.softwareAnalysisTeam.unitTestFuzzer.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RandoopGenerator(
    private val javaHome: String,
    private val randoopJarLocation: String
) : TestGenerator {

    override fun getTests(testClassName: String, projectCP: String): List<String> {
        val tests: MutableList<String> = mutableListOf()
        var generatedTestsDir: Path? = null
        try {
            generatedTestsDir = Files.createDirectory(Paths.get(projectCP, "randoopGeneratedTests"))

            val defaultCommand: String =
                javaHome + File.separator + "bin" + File.separator + "java" + " " + "-classpath" + " " + projectCP + File.pathSeparator + randoopJarLocation + " " + "randoop.main.Main gentests" +
                        " " + "--testclass=" + testClassName + " " + "--time-limit=5"

            CommandExecutor.execute(defaultCommand, generatedTestsDir.toString())

            Files.walk(generatedTestsDir).forEach {
                if (Files.isRegularFile(it)) {
                    tests.add(it.toFile().readText())
                }
            }
        }
        catch (e: Exception) {
            logger.error(e.stackTraceToString())
        }
        finally {
            generatedTestsDir?.toFile()?.deleteRecursively()
        }

        return tests

    }
}