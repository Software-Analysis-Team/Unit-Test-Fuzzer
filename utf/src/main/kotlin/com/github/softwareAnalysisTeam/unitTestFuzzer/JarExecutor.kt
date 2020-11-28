package com.github.softwareAnalysisTeam.unitTestFuzzer

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

class JarExecutor {

    companion object {
        fun execute(command: String, directory: String) {
            val processBuilder = ProcessBuilder()
            processBuilder.directory(File(directory))

            try {
                processBuilder.command(command.split(" "))
                logger.debug("Start a new process to run jar.")

                val process = processBuilder.start()
                process.waitFor()
                val exitValue = process.exitValue()

                if (exitValue != 0) {
                    logger.error("External jar execution failed. Error info:")
                    val errorStream = process.errorStream
                    val errorInfo = BufferedReader(InputStreamReader(errorStream)).lines()
                        .parallel().collect(Collectors.joining("\n"))
                    logger.error(errorInfo)
                } else {
                    logger.debug("External jar execution finished successfully.")
                }
                logger.debug(" Execution info:")
                val stream = process.inputStream
                val info = BufferedReader(InputStreamReader(stream)).lines()
                    .parallel().collect(Collectors.joining("\n"))
                logger.debug(info)
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }
        }
    }
}