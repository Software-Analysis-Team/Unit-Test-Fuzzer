package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File

val logger: Logger = KotlinLogging.logger {}
const val WRITING_BUDGET = 2
const val EXTERNAL_INSTRUMENTS_PERCENTAGE = 80
const val GENERATION_PERCENTAGE = 4

fun main(args: Array<String>) {

    val className = args[0]
    val timeBudget = args[1]
    val outputDirPath = File(args[2]).absolutePath.toString()
//    val classes = File(args[3]).absolutePath.toString()
    val cp = args[3] + ":$outputDirPath"
    // todo: change to relative paths or something
    val generatorJar = File(args[4]).absolutePath.toString()
    val JQFDir = File(args[5]).absolutePath.toString()

    // should generated tests be in the same package?
//    val splitClassName = className.split(".")
//    val simpleClassName = splitClassName.last()
//    val packageName = className.removeSuffix(".$simpleClassName")

    val totalBudget = timeBudget.toLong() - WRITING_BUDGET
    val externalInstrumentsBudget = totalBudget / 100.0 * EXTERNAL_INSTRUMENTS_PERCENTAGE

    val generationBudget = externalInstrumentsBudget / 100 * GENERATION_PERCENTAGE
    val fuzzingBudget = externalInstrumentsBudget - generationBudget

    val randoopGenerator = RandoopGenerator("$cp:$generatorJar")
    val tests = randoopGenerator.getTests(className, outputDirPath, generationBudget.toInt())

    var totalMethodCount = 0

    // todo: count number of methods in the same loop?
    for (i in tests.indices) {
        val parsedTest = StaticJavaParser.parse(tests[i])

        // todo: make a method that will do the same in one walk?
        parsedTest.removeTryCatchBlocks()
        parsedTest.walk(MethodDeclaration::class.java) {
            totalMethodCount++;
        }
    }

    val fuzzingBudgetPerMethod = fuzzingBudget / totalMethodCount.toFloat()

    for (i in tests.indices) {
        val parsedTest = StaticJavaParser.parse(tests[i])

        val testToFuzz = parsedTest.clone()
        testToFuzz.removeTryCatchBlocks()
        testToFuzz.removeAsserts()

        val placesToFuzz = SeedFinder.getSeeds(className, testToFuzz)
        if (placesToFuzz.isNotEmpty()) {
            val generatedValues =
                JQFZestFuzzer(outputDirPath, cp, JQFDir).getValues(
                    className,
                    testToFuzz,
                    placesToFuzz,
                    fuzzingBudgetPerMethod
                )

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