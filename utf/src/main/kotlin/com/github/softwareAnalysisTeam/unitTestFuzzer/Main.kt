package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File

internal val logger: Logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

//    val className = "com.hi.MyInteger"
//    val timeBudget = 10
//    val outputDir = "/Users/femilame/Documents/diploma/fuzzer-project/utf/src/main/resources/testcases"
//    val classes = "/Users/femilame/Documents/diploma/fuzzer-project/utf/src/main/resources"
//    val cp = "/Library/Java/JavaVirtualMachines/jdk1.8.0_241.jdk/Contents/Home:" +
//            "$outputDir:" +
//            "$classes"
//    val JQFDir = "/Users/femilame/Documents/diploma/fuzzer-project/utf/libs/jqf"
//    val randoopJar = "/Users/femilame/Documents/diploma/fuzzer-project/utf/libs/randoop-all-4.2.4.jar"
//
    val className = args[0]
    val timeBudget = args[1]
    val outputDir = File(args[2]).absolutePath.toString()
    val cp = args[3] + ":$outputDir"
//    todo: change to relative paths or something
    val randoopJar = File(args[4]).absolutePath.toString()
    val JQFDir = File(args[5]).absolutePath.toString()

    val splitClassName = className.split(".")
    val simpleClassName = splitClassName.last()
    val packageName = className.removeSuffix(".$simpleClassName")

    val randoopGenerator = RandoopGenerator(cp + ":" + randoopJar)
    val tests = randoopGenerator.getTests(className, outputDir)

    for (i in tests.indices) {
        val parsedTest = StaticJavaParser.parse(tests[i])

        val testToFuzz = parsedTest.clone()
        testToFuzz.removeTryCatchBlocks()
        testToFuzz.removeAsserts()

        val placesToFuzz = SeedFinder.getSeeds(className, testToFuzz)
        if (!placesToFuzz.isEmpty()) {
            val generatedValues = JQFZestFuzzer(outputDir, cp, JQFDir).getValues(className, testToFuzz, placesToFuzz)

            val testToConstruct = parsedTest.clone()
            testToConstruct.removeTryCatchBlocks()
            val placesForNewValues = SeedFinder.getSeeds(className, testToConstruct)

            TestCreator.createTest(i, parsedTest, testToConstruct, placesForNewValues, generatedValues, outputDir)
        }
    }
}