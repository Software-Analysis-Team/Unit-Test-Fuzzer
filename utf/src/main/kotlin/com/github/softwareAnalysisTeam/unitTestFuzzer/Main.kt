package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.EvosuiteGenerator
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File
import java.lang.Exception

val logger: Logger = KotlinLogging.logger {}
const val WRITING_BUDGET = 2
const val EXTERNAL_INSTRUMENTS_PERCENTAGE = 80
const val GENERATION_PERCENTAGE = 3

fun main(args: Array<String>) {

    val className = args[0]
    val timeBudget = args[1]
    val outputDirPath = File(args[2]).absolutePath.toString()
    var cp = args[3] + ":$outputDirPath"
    val generatorName = args[4]
    val generatorJar = File(args[5]).absolutePath.toString()
    val JQFDir = File(args[6]).absolutePath.toString()

    val splitClassName = className.split(".")
    val simpleClassName = splitClassName.last()
    var packageName: String? = null
    if (splitClassName.size > 1) {
        packageName = className.removeSuffix(".$simpleClassName")
    }

    var outputDirWithPackage = outputDirPath
    for (j in 0..splitClassName.size - 2) {
        outputDirWithPackage += "${File.separator}${splitClassName[j]}"
    }
    val packageDirFile = File(outputDirWithPackage)
    packageDirFile.mkdirs()
    cp += ":$outputDirWithPackage"

    val totalBudget = timeBudget.toLong() - WRITING_BUDGET
    val externalInstrumentsBudget = totalBudget / 100.0 * EXTERNAL_INSTRUMENTS_PERCENTAGE
    val generationBudget = externalInstrumentsBudget / 100 * GENERATION_PERCENTAGE
    val fuzzingBudget = externalInstrumentsBudget - generationBudget

    var generator: TestGenerator? = null
    when (generatorName) {
        "randoop" -> generator = RandoopGenerator("$cp:$generatorJar")
        "evosuite" -> generator = EvosuiteGenerator(generatorJar, cp)
    }
    if (generator == null) {
        throw Exception("Failed to initialise generator $generatorName")
    }

    val tests = generator.getTests(className, outputDirPath, 2)//generationBudget.toInt())

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
                JQFZestFuzzer(outputDirWithPackage, cp, JQFDir).getValues(
                    className,
                    packageName,
                    testToFuzz,
                    placesToFuzz,
                    fuzzingBudgetPerMethod
                )

            val testToConstruct = parsedTest.clone()
            testToConstruct.removeTryCatchBlocks()
            val placesForNewValues = SeedFinder.getSeeds(className, testToConstruct)

            TestCreator.createTest(
                i,
                parsedTest,
                testToConstruct,
                placesForNewValues,
                generatedValues,
                outputDirWithPackage,
                packageName,
                cp
            )
        }
    }
}