package com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.*
import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.Fuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.SeedFinder
import com.github.softwareAnalysisTeam.unitTestFuzzer.TestCreator.Companion.collectAndDeleteAsserts
import com.github.softwareAnalysisTeam.unitTestFuzzer.logger
import java.io.File
import java.nio.file.Paths

class JQFZestFuzzer : Fuzzer {
    private val commandToCompile: String
    private val commandToRun: String
    private val commandToRepr: String
    private var paths: String
    private val projectDir = Paths.get("").toAbsolutePath().toString()
    private val resourcesDir = Paths.get(projectDir, "src", "main", "resources").toString()
    private val generatedValuesFileName = "JQFGeneratedValues"

    init {
        val fileForPaths = File(projectDir + File.separator + "JQFPaths")
        fileForPaths.createNewFile()
        CommandExecutor.execute("libs/jqf/scripts/classpath.sh", projectDir, fileForPaths)
        paths =
            ".:" + fileForPaths.readText() + ":" + Paths.get("libs/javaparser-core-3.15.0.jar").toAbsolutePath().toString()
        fileForPaths.delete()

        commandToCompile = "javac -cp $paths"
        commandToRun = "timeout 10s ${Paths.get(projectDir, "libs/jqf/bin/jqf-zest")} -c $paths"
        commandToRepr = "${Paths.get(projectDir, "libs/jqf/bin/jqf-repro")} -c $paths"
    }

    override fun getValues(testingClassName: String, cu: CompilationUnit, seeds: List<Expression>): List<List<String>> {
        if (seeds.isEmpty()) {
            return mutableListOf()
        }

        // assume our testing class is in resourceDir
        // todo: fix it
        val className = "ClassToFuzz"
        val methodName = "methodToFuzz"

        val fileToFuzz = File(resourcesDir + File.separator + "$className.java")
        fileToFuzz.createNewFile()

        val fuzzingTest = cu.clone()
        collectAndDeleteAsserts(fuzzingTest)

        val fileWithGeneratedValues = File(resourcesDir + File.separator + generatedValuesFileName)
        fileWithGeneratedValues.createNewFile()

        val fuzzingSeed = SeedFinder.getSeeds(testingClassName, fuzzingTest)
        fileToFuzz.writeText(constructClassToFuzz(fuzzingTest, fuzzingSeed, className, methodName).toString())

        CommandExecutor.execute("$commandToCompile ${fileToFuzz.name} $testingClassName.java", resourcesDir)
        CommandExecutor.execute("$commandToRun $className $methodName", resourcesDir)
        // todo: iterate over all corpus/failures
        //CommandExecutor.execute("$commandToRepr $className $methodName fuzz-results/failures/id_000000", resourcesDir)


        fileWithGeneratedValues.delete()
        fileToFuzz.delete()
        File(resourcesDir + File.separator + "$className.class").delete()
        return mutableListOf<List<String>>()
    }

    private fun constructClassToFuzz(cu: CompilationUnit, seeds: List<Expression>, className: String, methodName: String): CompilationUnit {
        val fileToFuzz = CompilationUnit()

        addImports(fileToFuzz)

        val classToFuzz = fileToFuzz.addClass("$className", Modifier.Keyword.PUBLIC)
        val classAnnotation = SingleMemberAnnotationExpr().setMemberValue(NameExpr("JQF.class")).setName("RunWith")
        classToFuzz.addAnnotation(classAnnotation)

        val methodToFuzz = classToFuzz.addMethod("$methodName", Modifier.Keyword.PUBLIC)
        methodToFuzz.addThrownException(Exception::class.java)
        val methodAnnotation = MarkerAnnotationExpr().setName("Fuzz")
        methodToFuzz.addAnnotation(methodAnnotation)

        for (i in seeds.indices) {
            methodToFuzz.addParameter(Parameter(seeds[i].getType(), "methodToFuzzArgument$i"))
            seeds[i].parentNode.get().replace(seeds[i], NameExpr("methodToFuzzArgument$i"))
        }

        val methodsBodies = mutableListOf<String>()
        cu.walk(MethodDeclaration::class.java) {
            if (it.body.isPresent) {
                 methodsBodies.add("${it.body.get()}\n")
            }
        }

        val methodBody = constructBodyForMethodToFuzz(methodsBodies, methodToFuzz)
        methodToFuzz.setBody(methodBody)

        return fileToFuzz
    }

    private fun addImports(fileToFuzz: CompilationUnit) {
        fileToFuzz.apply {
            addImport("java.util.*")
            addImport("java.io.*")

            addImport("org.junit.runner.RunWith")
            addImport("com.pholser.junit.quickcheck.*")
            addImport("com.pholser.junit.quickcheck.generator.*")
            addImport("edu.berkeley.cs.jqf.fuzz.*")
        }
    }

    private fun constructBodyForMethodToFuzz(methodsBodies: List<String>, methodToFuzz: MethodDeclaration): BlockStmt {
        val bodyStmt = BlockStmt()

        // todo: ad as preprocessing step
        bodyStmt.addStatement("Boolean debug = false;")

        for (methodBody in methodsBodies) {
            bodyStmt.addStatement(methodBody)
        }

        return bodyStmt
    }

    private fun Expression.getType(): Type {
        return when (this) {
            is BooleanLiteralExpr -> PrimitiveType.booleanType()
            is IntegerLiteralExpr -> PrimitiveType.intType()
            is LongLiteralExpr -> PrimitiveType.longType()
            is DoubleLiteralExpr -> PrimitiveType.doubleType()
            is CharLiteralExpr -> PrimitiveType.charType()
            is StringLiteralExpr -> ClassOrInterfaceType("String")
            else -> throw Exception("Unknown type of expression argument $this.")
        }
    }
}