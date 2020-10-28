package com.github.softwareAnalysisTeam.unitTestFuzzer

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors


class JarExecutor {

    fun executeJar(basePath: String, args: List<String>) {
        val commandList: MutableList<String> = ArrayList()
        commandList.add("java")
        //commandList.add("-jar")
        //commandList.add(jarFilePath)
        commandList.addAll(args)

        val processBuilder = ProcessBuilder()
        processBuilder.directory(File(basePath))

        try {
            processBuilder.command(commandList)
            val process = processBuilder.start()
            process.waitFor()
            val exitValue = process.exitValue()
            if (exitValue != 0) {
                val errorStream = process.errorStream
                val errorInfo = BufferedReader(InputStreamReader(errorStream)).lines()
                        .parallel().collect(Collectors.joining("\n"))
                println(errorInfo)
            }
        } catch (e: Exception) {
            println(e.message)
        }
    }
}