package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger

internal val logger: Logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

    val className = "MyInteger"
    val javaHome = "/Library/Java/JavaVirtualMachines/jdk1.8.0_241.jdk/Contents/Home"
    val projectLocation = "/Users/femilame/Documents/diploma/fuzzer-project/utf"
    val resourceLocation = "$projectLocation/src/main/resources"
    val randoopJar = "/Users/femilame/Documents/diploma/fuzzer-project/utf/libs/randoop-all-4.2.4.jar"

    val randoopGenerator = RandoopGenerator(javaHome, randoopJar)
    val tests = randoopGenerator.getTests(className, resourceLocation)

    // todo: replace with for loop?
    val test = StaticJavaParser.parse(tests[0])

    val testToFuzz = test.clone()
    testToFuzz.removeTryCatchBlocks()
    testToFuzz.removeAsserts()

    val placesToFuzz = SeedFinder.getSeeds(className, testToFuzz)
    val generatedValues = JQFZestFuzzer().getValues(className, testToFuzz, placesToFuzz)

    val testToConstruct = test.clone()
    testToConstruct.removeTryCatchBlocks()
    val placesForNewValues = SeedFinder.getSeeds(className, testToConstruct)

    TestCreator.createTest(test, testToConstruct, placesForNewValues, generatedValues, projectLocation)
}