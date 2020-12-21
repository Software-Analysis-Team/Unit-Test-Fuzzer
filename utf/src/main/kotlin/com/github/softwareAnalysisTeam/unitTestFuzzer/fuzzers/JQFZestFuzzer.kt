package com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.*
import com.github.javaparser.ast.body.*
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
    protected val JQFgeneratedValuesDirName = "JQFGeneratedValuesForTests"

    init {
        val fileForPaths = File(projectDir + File.separator + "JQFPaths")
        fileForPaths.createNewFile()
        CommandExecutor.execute("libs/jqf/scripts/classpath.sh", projectDir, fileForPaths)
        paths = ".:" + fileForPaths.readText()
        fileForPaths.delete()

        commandToCompile = "javac -cp $paths"
        commandToRun = "timeout 1s ${Paths.get(projectDir, "libs/jqf/bin/jqf-zest")} -c $paths"
        commandToRepr = "${Paths.get(projectDir, "libs/jqf/bin/jqf-repro")} -c $paths"
    }

    override fun getValues(testingClassName: String, cu: CompilationUnit, seeds: List<Expression>): List<List<String>> {
        if (seeds.isEmpty()) {
            logger.debug("List with seeds is empty.")
            return mutableListOf()
        }

        // assume our testing class is in resourceDir
        // todo: fix it
        val classForFuzzingName = "ClassForFuzzing"
        val classForSavingName = "ClassForSaving"
        //val methodName = "methodForFuzzing"

        val fileForFuzzing = File(resourcesDir + File.separator + "$classForFuzzingName.java")
        fileForFuzzing.createNewFile()

        val fileForSaving = File(resourcesDir + File.separator + "$classForSavingName.java")
        fileForSaving.createNewFile()

        val testToFuzz = cu.clone()
        collectAndDeleteAsserts(testToFuzz)

//        val fileWithGeneratedValues = File(resourcesDir + File.separator + generatedValuesFileName)
//        fileWithGeneratedValues.createNewFile()

        val fuzzingSeeds = SeedFinder.getSeeds(testingClassName, testToFuzz)
        val map = fuzzingSeeds.second

        val classForFuzzing = constructClassToFuzz(testToFuzz, fuzzingSeeds, classForFuzzingName)
        fileForFuzzing.writeText(classForFuzzing.toString())

        val classForSaving = classForFuzzing.clone()
        constructClassForSaving(classForSaving)
        fileForSaving.writeText(classForSaving.toString())

        CommandExecutor.execute(
            "$commandToCompile $testingClassName.java ${fileForFuzzing.name} ${fileForSaving.name}",
            resourcesDir
        )

        val generatedValuesDir = (Paths.get(projectDir, "$JQFgeneratedValuesDirName")).toFile()
        if (!generatedValuesDir.exists()) {
            generatedValuesDir.mkdir()
        }

        try {

            CommandExecutor.execute("$commandToRun $classForFuzzingName test018", resourcesDir)
            CommandExecutor.execute(
                "$commandToRepr $classForSavingName test018 fuzz-results/corpus/id_000000",
                resourcesDir
            )

            //CommandExecutor.execute(defaultCommand, generatedTestsDir.toString())

            //        map.forEach {
//            CommandExecutor.execute("$commandToRun $classForFuzzingName ${it.key}", resourcesDir)
//
//            // todo: iterate over all corpus/failures
//            CommandExecutor.execute("$commandToRepr $classForSavingName ${it.key} fuzz-results/failures/id_000000", resourcesDir)
//        }


//            Files.walk(generatedTestsDir).forEach {
//                if (Files.isRegularFile(it)) {
//                    tests.add(it.toFile().readText())
//                }
//            }
        } catch (e: Exception) {
            logger.error(e.stackTraceToString())
        }

        //fileWithGeneratedValues.delete()
        //fileForFuzzing.delete()
        //fileForSaving.delete()

        File(resourcesDir + File.separator + "$classForFuzzingName.class").delete()
        File(resourcesDir + File.separator + "$classForSavingName.class").delete()

        return mutableListOf<List<String>>()
    }

    private fun constructClassForSaving(classForFuzzing: CompilationUnit) {
        classForFuzzing.walk(ClassOrInterfaceDeclaration::class.java) {
            if (it.nameAsString == "ClassForFuzzing") it.setName("ClassForSaving")
        }

        classForFuzzing.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
            val tryStmt = TryStmt()
            val blockStmt = BlockStmt()
            blockStmt.addStatement("File file = new File(\"$JQFgeneratedValuesDirName/generatedValuesFrom_${testMethodDeclaration.nameAsString}\");")
            blockStmt.addStatement("file.createNewFile();")
            blockStmt.addStatement("FileWriter writer = new FileWriter(file);")
            for (parameter in testMethodDeclaration.parameters) {
                blockStmt.addStatement("writer.write(${parameter.name} + \"\\n\");")
            }
            blockStmt.addStatement("writer.close();")
            tryStmt.tryBlock = blockStmt

            val catchClause = CatchClause()
            val parameter = Parameter()
            parameter.setName("e")
            parameter.type = ClassOrInterfaceType("Exception")
            catchClause.parameter = parameter
            val catchStmt = BlockStmt()
            catchStmt.addStatement(StaticJavaParser.parseStatement("System.out.println(\"Exception\");"))
            catchClause.body = catchStmt
            val stmts: NodeList<CatchClause> = NodeList()
            stmts.add(catchClause)
            tryStmt.catchClauses = stmts

            val body = BlockStmt()
            body.addStatement(tryStmt)

            testMethodDeclaration.setBody(body)
        }
    }

    private fun constructClassToFuzz(
        cu: CompilationUnit,
        seeds: Pair<List<Expression>, Map<String, List<Expression>>>,
        className: String
    ): CompilationUnit {
        val fileToFuzz = CompilationUnit()

        addImports(fileToFuzz)

        val classToFuzz = fileToFuzz.addClass(className, Modifier.Keyword.PUBLIC)
        val classAnnotation = SingleMemberAnnotationExpr().setMemberValue(NameExpr("JQF.class")).setName("RunWith")
        classToFuzz.addAnnotation(classAnnotation)
        classToFuzz.members.add(
            FieldDeclaration().addVariable(
                VariableDeclarator(
                    PrimitiveType.booleanType(),
                    "debug",
                    BooleanLiteralExpr(false)
                )
            )
        )

        val methodAnnotation = MarkerAnnotationExpr().setName("Fuzz")

        cu.walk(MethodDeclaration::class.java) {
            val fuzzingMethod = classToFuzz.addMethod(it.nameAsString, Modifier.Keyword.PUBLIC)
            fuzzingMethod.setUpMethodForFuzzing(
                it,
                methodAnnotation,
                seeds.second[it.nameAsString] as MutableList<Expression>
            )
        }

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

    private fun MethodDeclaration.setUpMethodForFuzzing(
        testMethod: MethodDeclaration,
        methodAnnotation: AnnotationExpr,
        seeds: List<Expression>
    ) {
        for (i in seeds.indices) {
            val node = seeds[i]
            this.addParameter(Parameter(node.getType(), "methodToFuzzArgument${i}"))
            node.parentNode.get().replace(node, NameExpr("methodToFuzzArgument${i}"))
        }

        this.addAnnotation(methodAnnotation)
        if (testMethod.body.isPresent) {
            this.setBody(testMethod.body.get())
        }
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