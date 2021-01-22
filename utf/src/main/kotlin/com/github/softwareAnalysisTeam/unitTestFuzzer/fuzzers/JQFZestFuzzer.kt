package com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.*
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.*
import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.Fuzzer
import com.github.softwareAnalysisTeam.unitTestFuzzer.logger
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths


class JQFZestFuzzer : Fuzzer {
    private val commandToCompile: String
    private val commandToRun: String
    private val commandToRepr: String
    private var paths: String
    private val projectDir = Paths.get("").toAbsolutePath().toString()
    private val resourcesDir = Paths.get(projectDir, "src", "main", "resources").toString()
    private val JQFgeneratedValuesDirName = "JQFGeneratedValuesForTests"

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

    override fun getValues(
        testingClassName: String,
        testToFuzz: CompilationUnit,
        seeds: Map<String, List<Expression>>
    ): Map<String, List<String>> {
        if (seeds.isEmpty()) {
            logger.debug("List with seeds is empty.")
            return mutableMapOf()
        }

        // assume our testing class is in resourceDir
        // todo: fix it
        val classForFuzzingName = "ClassForFuzzing"
        val classForSavingName = "ClassForSaving"

        val fileForFuzzing = File(resourcesDir + File.separator + "$classForFuzzingName.java")
        fileForFuzzing.createNewFile()

        val fileForSaving = File(resourcesDir + File.separator + "$classForSavingName.java")
        fileForSaving.createNewFile()

        val classForFuzzing = constructClassToFuzz(testToFuzz, seeds, classForFuzzingName)
        fileForFuzzing.writeText(classForFuzzing.toString())

        val classForSaving = constructClassForSaving(classForFuzzing)
        fileForSaving.writeText(classForSaving.toString())

        CommandExecutor.execute(
            "$commandToCompile $testingClassName.java ${fileForFuzzing.name} ${fileForSaving.name}",
            resourcesDir
        )

        val generatedValuesDir = (Paths.get(resourcesDir, JQFgeneratedValuesDirName)).toFile()
        if (!generatedValuesDir.exists()) {
            generatedValuesDir.mkdir()
        }

        val fuzzResultsPath = Paths.get(resourcesDir, "fuzz-results")
        val corpusFuzzResultsPath = Paths.get(fuzzResultsPath.toString(), "corpus")
        val failureFuzzResultsPath = Paths.get(fuzzResultsPath.toString(), "failures")

        try {
            seeds.keys.forEach { methodName ->
                CommandExecutor.execute("$commandToRun $classForFuzzingName $methodName", resourcesDir)

                Files.walk(corpusFuzzResultsPath).forEach { path ->
                    if (Files.isRegularFile(path)) {
                        CommandExecutor.execute("$commandToRepr $classForSavingName $methodName $path", resourcesDir)
                    }
                }

//                Files.walk(failureFuzzResultsPath).forEach { path ->
//                    if (Files.isRegularFile(path)) {
//                        CommandExecutor.execute("$commandToRepr $classForSavingName $methodName $path", resourcesDir)
//                    }
//                }
            }

        } catch (e: Exception) {
            logger.error(e.stackTraceToString())
        } finally {
            fuzzResultsPath.toFile().deleteRecursively()
        }

        fileForFuzzing.delete()
        fileForSaving.delete()

        File(resourcesDir + File.separator + "$classForFuzzingName.class").delete()
        File(resourcesDir + File.separator + "$classForSavingName.class").delete()

        val valuesForEachTest = mutableMapOf<String, List<String>>()

        Files.walk(generatedValuesDir.toPath()).forEach {
            if (Files.isRegularFile(it)) {
                val methodAndFileName = it.fileName.toString()
                val listOfValues = mutableListOf<String>()
                var fileInputStream: FileInputStream? = null
                var objectInputStream: ObjectInputStream? = null

                try {
                    fileInputStream =
                        FileInputStream(generatedValuesDir.toString() + File.separator + methodAndFileName)
                    objectInputStream = ObjectInputStream(fileInputStream)

                    while (true) {
                        listOfValues.add(objectInputStream.readObject().toString())
                    }
                } catch (e: EOFException) {
                    // todo: find a better way to stop reading from file
                } catch (e: Exception) {
                    logger.error(e.stackTraceToString())
                } finally {
                    objectInputStream?.close()
                    fileInputStream?.close()
                }

                valuesForEachTest[methodAndFileName] = listOfValues
            }
        }

        generatedValuesDir.deleteRecursively()

        return valuesForEachTest
    }

    private fun constructClassForSaving(classForFuzzing: CompilationUnit): CompilationUnit {
        val classForSaving = classForFuzzing.clone()
        classForSaving.walk(ClassOrInterfaceDeclaration::class.java) {
            if (it.nameAsString == "ClassForFuzzing") it.setName("ClassForSaving")
        }

        classForSaving.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
            val tryStmt = TryStmt()
            val blockStmt = BlockStmt()
            blockStmt.addStatement("File file = new File(\"$JQFgeneratedValuesDirName/${testMethodDeclaration.nameAsString}\");")
            blockStmt.addStatement("file.createNewFile();")
            blockStmt.addStatement("OutputStream fileOutputStream = new FileOutputStream(file);")
            blockStmt.addStatement("ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);")

            for (parameter in testMethodDeclaration.parameters) {
                blockStmt.addStatement("objectOutputStream.writeObject(${parameter.name});")
            }
            blockStmt.addStatement("objectOutputStream.close();")
            blockStmt.addStatement("fileOutputStream.close();")

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
        return classForSaving
    }

    private fun constructClassToFuzz(
        cu: CompilationUnit,
        seeds: Map<String, List<Expression>>,
        className: String
    ): CompilationUnit {
        val fileToFuzz = CompilationUnit()

        addImports(fileToFuzz)

        val classToFuzz = fileToFuzz.addClass(className, Modifier.Keyword.PUBLIC)
        val classAnnotation = SingleMemberAnnotationExpr().setMemberValue(NameExpr("JQF.class")).setName("RunWith")
        classToFuzz.addAnnotation(classAnnotation)

        cu.walk(FieldDeclaration::class.java) {
            classToFuzz.addMember(it)
        }

        val methodAnnotation = MarkerAnnotationExpr().setName("Fuzz")

        cu.walk(MethodDeclaration::class.java) {
            val fuzzingMethod = classToFuzz.addMethod(it.nameAsString, Modifier.Keyword.PUBLIC)
            fuzzingMethod.setUpMethodForFuzzing(
                it,
                methodAnnotation,
                seeds[it.nameAsString] as MutableList<Expression>
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