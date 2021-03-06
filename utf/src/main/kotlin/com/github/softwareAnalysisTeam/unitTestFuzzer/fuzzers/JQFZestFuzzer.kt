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


class JQFZestFuzzer(private val outputDir: String, private var cp: String, private val JQFDir: String) : Fuzzer {
    private val commandToCompile: String
    private val commandToRepr: String
    private val JQFgeneratedValuesDirName = "JQFGeneratedValuesForTests"

    init {
        val fileForPaths = File(outputDir + File.separator + "JQFPaths")
        fileForPaths.createNewFile()
        CommandExecutor.execute("$JQFDir/scripts/classpath.sh", outputDir, fileForPaths)
        this.cp = "$cp:${fileForPaths.readText()}:${Paths.get(".")}"
        fileForPaths.delete()

        commandToCompile = "javac -cp ${this.cp}"
        commandToRepr = "${Paths.get(JQFDir, "bin", "jqf-repro")} -c ${this.cp}"
    }

    override fun getValues(
        testingClassName: String,
        cu: CompilationUnit,
        seeds: Map<String, List<Expression>>,
        budgetPerMethod: Double
    ): Map<String, List<String>> {
        if (seeds.isEmpty()) {
            logger.debug("Map with seeds is empty.")
            return mutableMapOf()
        }

        val commandToRun = "timeout ${budgetPerMethod}s ${Paths.get(JQFDir, "bin", "jqf-zest")} -c ${this.cp}"

        val classForFuzzingName = "ClassForFuzzing"
        val classForSavingName = "ClassForSaving"

        val fileForFuzzing = File(outputDir + File.separator + "$classForFuzzingName.java")
        fileForFuzzing.createNewFile()

        val fileForSaving = File(outputDir + File.separator + "$classForSavingName.java")
        fileForSaving.createNewFile()

        val classForFuzzing = constructClassToFuzz(cu, seeds, classForFuzzingName)
        fileForFuzzing.writeText(classForFuzzing.toString())

        val classForSaving = constructClassForSaving(classForFuzzing)
        fileForSaving.writeText(classForSaving.toString())

        CommandExecutor.execute(
            "$commandToCompile ${fileForFuzzing.name} ${fileForSaving.name}",
            outputDir
        )

        val generatedValuesDir = (Paths.get(outputDir, JQFgeneratedValuesDirName)).toFile()
        if (!generatedValuesDir.exists()) {
            generatedValuesDir.mkdir()
        }

        val fuzzResultsPath = Paths.get(outputDir, "fuzz-results")

        val corpusFuzzResultsPath = Paths.get(fuzzResultsPath.toString(), "corpus")
        val failureFuzzResultsPath = Paths.get(fuzzResultsPath.toString(), "failures")

        try {
            seeds.keys.forEach { methodName ->
                CommandExecutor.execute("$commandToRun $classForFuzzingName $methodName", outputDir)
                if (Files.exists(corpusFuzzResultsPath)) {
                    Files.walk(corpusFuzzResultsPath).forEach { path ->
                        if (Files.isRegularFile(path)) {
                            CommandExecutor.execute(
                                "$commandToRepr $classForSavingName $methodName $path",
                                outputDir, File("$fuzzResultsPath/logs")
                            )
                        }
                    }
                }

//                Files.walk(failureFuzzResultsPath).forEach { path ->
//                    if (Files.isRegularFile(path)) {
//                        CommandExecutor.execute("$commandToRepr $classForSavingName $methodName $path", outputDir)
//                    }
//                }

            }
        } catch (e: Exception) {
            logger.error(e.stackTraceToString())
        } finally {
            fuzzResultsPath.toFile().deleteRecursively()

            fileForFuzzing.delete()
            fileForSaving.delete()
            File(outputDir + File.separator + "$classForFuzzingName.class").delete()
            File(outputDir + File.separator + "$classForSavingName.class").delete()
        }

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

        logger.debug(valuesForEachTest.toString())

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