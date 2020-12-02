package com.github.softwareAnalysisTeam.unitTestFuzzer

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

class CommandExecutor {

    companion object {
        fun execute(command: String, directory: String) {
            val processBuilder = ProcessBuilder()
            processBuilder.directory(File(directory))
            processBuilder.command(command.split(" "))
            var process: Process? = null

            try {
                logger.debug("Start a new process to run command.")
                process = processBuilder.start()
                process.waitFor()
                val exitValue = process.exitValue()

                if (exitValue != 0) {
                    logger.error("External command execution failed. Error info:")
                    val errorStream = process.errorStream
                    val errorInfo = BufferedReader(InputStreamReader(errorStream)).lines()
                        .parallel().collect(Collectors.joining("\n"))
                    logger.error(errorInfo)
                } else {
                    logger.debug("External command execution finished successfully.")
                }
                logger.debug("Execution info:")
                val stream = process.inputStream
                val info = BufferedReader(InputStreamReader(stream)).lines()
                    .parallel().collect(Collectors.joining("\n"))
                logger.debug(info)

                process.destroy()
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
                process?.destroy()
            }
        }
    }
}