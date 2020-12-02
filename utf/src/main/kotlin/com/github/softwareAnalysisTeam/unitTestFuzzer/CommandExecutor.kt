package com.github.softwareAnalysisTeam.unitTestFuzzer

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

class CommandExecutor {

    companion object {
        fun execute(command: String, directory: String) {
            execute(command, directory, null)
        }

        fun execute(command: String, directory: String, outputFile: File?) {
            val processBuilder = ProcessBuilder()
            processBuilder.directory(File(directory))

            if (outputFile != null) {
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile))
            }

            processBuilder.command(command.split(" "))
            var process: Process? = null

            try {
                logger.debug("Start a new process to run command: $command.")
                process = processBuilder.start()
                process.waitFor()
                val exitValue = process.exitValue()

                if (exitValue != 0) {
                    logger.error("External command execution failed.")
                    val errorStream = process.errorStream
                    if (errorStream.available() != 0) {
                        logger.error("Error info:")
                        val errorInfo = BufferedReader(InputStreamReader(errorStream)).lines()
                            .parallel().collect(Collectors.joining("\n"))
                        logger.error(errorInfo)
                    }
                } else {
                    logger.debug("External command execution finished successfully.")
                }

                val stream = process.inputStream
                if (stream.available() != 0) {
                    logger.debug("Execution info:")
                    val info = BufferedReader(InputStreamReader(stream)).lines()
                        .parallel().collect(Collectors.joining("\n"))
                    logger.debug(info)
                }

                process.destroy()
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
                process?.destroy()
            }
        }
    }
}