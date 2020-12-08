package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.EvosuiteGenerator
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger

internal val logger: Logger = KotlinLogging.logger {}

fun main(args: Array<String>) {

    val className = "MyInteger"
    val javaHome = "/Library/Java/JavaVirtualMachines/jdk1.8.0_241.jdk/Contents/Home"
    val buildLocation = "/Users/femilame/Documents/diploma/fuzzer-project/utf/src/main/resources"
    val randoopJar = "/Users/femilame/Documents/diploma/fuzzer-project/utf/libs/randoop-all-4.2.4.jar"

    val randoopGenerator = RandoopGenerator(javaHome, randoopJar)
    val tests = randoopGenerator.getTests(className, buildLocation)

    // working with only one test file
    // todo: replace with for loop
    val parsed = JunitParser.parse(className, tests[0])
    val test = parsed.first
    val seeds = parsed.second
    val values = JQFZestFuzzer().getValues(className, test, seeds)

    TestCreator.createTest(test, seeds, values[0])
}