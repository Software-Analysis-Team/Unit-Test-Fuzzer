package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.Expression
import com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers.JQFZestFuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.EvosuiteGenerator
import com.github.softwareAnalysisTeam.unitTestFuzzer.generators.RandoopGenerator
import mu.KotlinLogging
import org.slf4j.Logger
import java.io.File
import java.lang.Exception

val logger: Logger = KotlinLogging.logger {}
const val WRITING_BUDGET = 2
const val EXTERNAL_INSTRUMENTS_PERCENTAGE = 70
const val GENERATION_PERCENTAGE = 10

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

    // todo: set test package Name as argument
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
    val generationBudget = 3//externalInstrumentsBudget / 100 * GENERATION_PERCENTAGE
    val fuzzingBudget = externalInstrumentsBudget - generationBudget

    val generator: TestGenerator?
    generator = when (generatorName) {
        "randoop" -> RandoopGenerator("$cp:$generatorJar")
        "evosuite" -> EvosuiteGenerator(generatorJar, cp)
        else -> throw Exception("Unknown generator $generatorName")
    }

    val tests = generator.getTests(className, outputDirPath, generationBudget.toInt(), packageName)
    if (tests.isEmpty()) throw Exception("Generator $generatorName produced no tests to fuzz")

    val parsedTests: MutableList<CompilationUnit> = mutableListOf()
    val allMethods: MutableList<MethodDeclaration> = mutableListOf()

    for (i in tests.indices) {
        val parsedTest = StaticJavaParser.parse(tests[i])
        parsedTests.add(parsedTest)

        parsedTest.walk(MethodDeclaration::class.java) {
            allMethods.add(it.setName("${it.name}ofFile$i"))
        }
    }

    var fuzzingBudgetPerMethod = 5.0
    val numberOfMethodToFuzz = fuzzingBudget / fuzzingBudgetPerMethod
    val methodsToFuzz: List<MethodDeclaration>

    val classOfAllMethods = constructClassOfMethods(allMethods, parsedTests)
    val allMethodsSeeds = SeedFinder.getSeeds(className, classOfAllMethods)

    val methodsWithSeeds = allMethodsSeeds.keys.toList()

    if (numberOfMethodToFuzz > methodsWithSeeds.size) {
        methodsToFuzz = methodsWithSeeds
        fuzzingBudgetPerMethod = fuzzingBudget / allMethods.size.toFloat()
    } else {
        allMethods.shuffle()
        methodsToFuzz = methodsWithSeeds.take(numberOfMethodToFuzz.toInt())
    }

    val classOfMethodsToFuzz = constructClassOfMethods(methodsToFuzz, parsedTests)
    val testToFuzz = classOfMethodsToFuzz.clone()
    testToFuzz.removeTryCatchBlocks()
    testToFuzz.removeAsserts()

    val placesToFuzz = SeedFinder.getSeeds(className, testToFuzz)

    var generatedValues: Map<String, List<String>> = mapOf()
    var placesForNewValues: Map<MethodDeclaration, List<Expression>> = mapOf()
    val testToConstruct = classOfMethodsToFuzz.clone()

    if (placesToFuzz.isNotEmpty()) {
        logger.debug("Chosen places to fuzz\n: $placesToFuzz")
        generatedValues =
            JQFZestFuzzer(outputDirWithPackage, cp, JQFDir).getValues(
                className,
                packageName,
                testToFuzz,
                placesToFuzz,
                fuzzingBudgetPerMethod
            )

        testToConstruct.removeTryCatchBlocks()
        placesForNewValues = SeedFinder.getSeeds(className, testToConstruct)
    } else {
        logger.debug("No places to fuzz were found")
    }

    TestCreator.createTest(
        classOfAllMethods,
        testToConstruct,
        placesForNewValues,
        generatedValues,
        outputDirWithPackage,
        packageName,
        cp
    )
}

fun constructClassOfMethods(methods: List<MethodDeclaration>, originalTests: List<CompilationUnit>): CompilationUnit {
    val cu = CompilationUnit()
    val createdClass = cu.addClass("RegressionTests", Modifier.Keyword.PUBLIC)

    originalTests.forEach { compilationUnit ->
        compilationUnit.walk(ClassOrInterfaceDeclaration::class.java) { classOrInterfaceDeclaration ->
            for (member in classOrInterfaceDeclaration.members) {
                if (!member.isMethodDeclaration && !createdClass.members.contains(member)) {
                    createdClass.addMember(member)
                }
            }

            classOrInterfaceDeclaration.walk(ImportDeclaration::class.java) {
                cu.addImport(it)
            }
        }
    }

    for (method in methods) {
        createdClass.addMember(method)
    }

    return cu
}