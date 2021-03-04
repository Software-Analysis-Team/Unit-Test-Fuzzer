package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File

val logger: Logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

    val className = args[0]
    val timeBudget = args[1]
    val outputDirPath = File(args[2]).absolutePath.toString()
//    val classes = File(args[3]).absolutePath.toString()
    val cp = args[3] + ":$outputDirPath"
    // todo: change to relative paths or something
    val randoopJar = File(args[4]).absolutePath.toString()
    val JQFDir = File(args[5]).absolutePath.toString()


    // should generated tests be in the same package?
//    val splitClassName = className.split(".")
//    val simpleClassName = splitClassName.last()
//    val packageName = className.removeSuffix(".$simpleClassName")

    val randoopGenerator = RandoopGenerator("$cp:$randoopJar")
    val tests = randoopGenerator.getTests(className, outputDirPath)

    for (i in tests.indices) {
        val parsedTest = StaticJavaParser.parse(tests[i])

        val testToFuzz = parsedTest.clone()
        testToFuzz.removeTryCatchBlocks()
        testToFuzz.removeAsserts()

        val placesToFuzz = SeedFinder.getSeeds(className, testToFuzz)
        if (!placesToFuzz.isEmpty()) {
            val generatedValues =
                JQFZestFuzzer(outputDirPath, cp, JQFDir).getValues(className, testToFuzz, placesToFuzz)

            val testToConstruct = parsedTest.clone()
            testToConstruct.removeTryCatchBlocks()
            val placesForNewValues = SeedFinder.getSeeds(className, testToConstruct)

//            var packageDir = outputDir
//            for (j in 0..splitClassName.size - 2) {
//                packageDir += "${File.separator}${splitClassName[j]}"
//            }
//
//            val packageDirFile = File(packageDir)
//
//            if (!packageDirFile.exists()) {
//                packageDirFile.mkdir()
//            }

            TestCreator.createTest(
                i,
                parsedTest,
                testToConstruct,
                placesForNewValues,
                generatedValues,
                outputDirPath,
                cp
            )
        }
    }
}