package com.github.softwareAnalysisTeam.unitTestFuzzer.generators

import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestGenerator
import com.github.softwareAnalysisTeam.unitTestFuzzer.logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class RandoopGenerator(
    private val cp: String
) : TestGenerator {

    override fun getTests(testClassName: String, outputDir: String, timeout: Int, testPackageName: String?): List<String> {
        val tests: MutableList<String> = mutableListOf()
        var generatedTestsDir: File? = null
        try {
            generatedTestsDir = Paths.get(outputDir, "randoopGeneratedTests").toFile()
            if (!generatedTestsDir.exists()) {
                generatedTestsDir.mkdir()
            }

            var defaultCommand =
                "java -classpath $cp randoop.main.Main gentests " +
                        "--testclass=$testClassName " +
                        "--time-limit=$timeout"

            if (testPackageName != null) {
                defaultCommand += " --junit-package-name=$testPackageName"
            }

            CommandExecutor.execute(defaultCommand, generatedTestsDir.toString())

            Files.walk(generatedTestsDir.toPath()).forEach {
                if (Files.isRegularFile(it)) {
                    tests.add(it.toFile().readText())
                }
            }
        } catch (e: Exception) {
            logger.error(e.stackTraceToString())
        } finally {
            generatedTestsDir?.deleteRecursively()
        }

        return tests
    }
}