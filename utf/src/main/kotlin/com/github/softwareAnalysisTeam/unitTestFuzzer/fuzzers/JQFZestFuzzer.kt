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
import java.io.File
import java.io.FileReader
import java.nio.file.Paths

class JQFZestFuzzer : Fuzzer {
    private val commandToCompile: String
    private val commandToRun: String
    private var classPaths: String
    private val projectDir = Paths.get("").toAbsolutePath().toString()
    private val generatedValuesFileName = "JQFGeneratedValues"

    init {
        val fileForPaths = File(projectDir + File.separator + "JQFPaths")
        fileForPaths.createNewFile()
        CommandExecutor.execute("libs/jqf/scripts/classpath.sh", projectDir, fileForPaths)
        classPaths =
            fileForPaths.readText() + ":" + Paths.get("libs/javaparser-core-3.15.0.jar").toAbsolutePath().toString()
        fileForPaths.delete()

        commandToCompile = "javac -cp .:$classPaths"
        commandToRun = "timeout 10s ${Paths.get(projectDir, "libs/jqf/bin/jqf-zest")} -c .:$classPaths"
    }

    override fun getValues(testingClassName: String, cu: CompilationUnit, seeds: List<Expression>): List<List<String>> {
        if (seeds.isEmpty()) {
            return mutableListOf()
        }

        // assume our testing class is in resourceDir
        // todo: fix it
        val resourcesDir = Paths.get(projectDir, "src", "main", "resources").toString()
        val className = "ClassToFuzz"
        val methodName = "MethodToFuzz"

        val fileToFuzz = File(resourcesDir + File.separator + "$className.java")
        fileToFuzz.createNewFile()

        val fuzzingTest = cu.clone()
        collectAndDeleteAsserts(fuzzingTest)

        val fuzzingSeed = SeedFinder.getSeeds(testingClassName, fuzzingTest)
        //prepareTestForFuzzing(fuzzingTest, fuzzingSeed)

        fileToFuzz.writeText(constructClassToFuzz(fuzzingTest, fuzzingSeed).toString())

        CommandExecutor.execute("$commandToCompile ${fileToFuzz.name} $testingClassName.java", resourcesDir)
        CommandExecutor.execute("$commandToRun $className $methodName", resourcesDir)

        val listOfValueLists = mutableListOf<List<String>>()
        val fileWithGeneratedValues = File(resourcesDir + File.separator + generatedValuesFileName)
        fileWithGeneratedValues.createNewFile()
        val fileReader = FileReader(fileWithGeneratedValues)

        val lines = fileReader.readLines()
        for (i in lines.indices step seeds.size) {
            val valueList = mutableListOf<String>()
            for (j in i until i + seeds.size) {
                valueList.add(lines[j])
            }
            listOfValueLists.add(valueList)
        }

        fileReader.close()
        fileWithGeneratedValues.delete()
        // fileToFuzz.delete()
        File(resourcesDir + File.separator + "$className.class").delete()
        File(resourcesDir + File.separator + "$testingClassName.class").delete()

        return listOfValueLists
    }

    private fun constructClassToFuzz(cu: CompilationUnit, seeds: List<Expression>): CompilationUnit {
        val fileToFuzz = CompilationUnit()

        addImports(fileToFuzz)

        val classToFuzz = fileToFuzz.addClass("ClassToFuzz", Modifier.Keyword.PUBLIC)
        val classAnnotation = SingleMemberAnnotationExpr().setMemberValue(NameExpr("JQF.class")).setName("RunWith")
        classToFuzz.addAnnotation(classAnnotation)

        val methodToFuzz = classToFuzz.addMethod("MethodToFuzz", Modifier.Keyword.PUBLIC)
        val methodAnnotation = MarkerAnnotationExpr().setName("Fuzz")
        methodToFuzz.addAnnotation(methodAnnotation)

        for (i in seeds.indices) {
            methodToFuzz.addParameter(Parameter(seeds[i].getType(), "methodToFuzzArgument$i"))
            seeds[i].parentNode.get().replace(seeds[i], StringLiteralExpr("methodToFuzzArgument$i"))
        }

        var methodsBody = mutableListOf<String>()
        cu.walk(MethodDeclaration::class.java) {
            if (it.body.isPresent) {
                 methodsBody.add("${it.body.get()}\n")
            }
        }

        val methodBody = constructBodyForMethodToFuzz(cu.toString(), methodToFuzz)
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

            addImport("com.github.javaparser.ast.expr.*")
            addImport("com.github.javaparser.ast.CompilationUnit")
            addImport("com.github.javaparser.StaticJavaParser")

            addImport("java.nio.charset.StandardCharsets")
            addImport("java.nio.file.Files")
            addImport("java.nio.file.Paths")
        }
    }

    private fun constructBodyForMethodToFuzz(test: String, methodToFuzz: MethodDeclaration): BlockStmt {
        val tryStmt = TryStmt()
        val blockStmt = BlockStmt()

        blockStmt.addStatement(test)

        blockStmt.addStatement("File file = new File(\"$generatedValuesFileName\");")
        blockStmt.addStatement("FileWriter writer = new FileWriter(file, true);")
        for (parameter in methodToFuzz.parameters) {
            blockStmt.addStatement("writer.write(${parameter.name} + \"\\n\");")
        }
        blockStmt.addStatement("writer.close();")

        tryStmt.tryBlock = blockStmt

        val catchClause = CatchClause().setParameter(Parameter(ClassOrInterfaceType("Exception"), "e"))
        val catchStmt = BlockStmt()

        // adding statements in catch block
        catchStmt.addStatement(StaticJavaParser.parseStatement("e.getStackTrace();"))
        catchClause.body = catchStmt

        val sts: NodeList<CatchClause> = NodeList()
        sts.add(catchClause)
        tryStmt.catchClauses = sts
        val bodyStmts = NodeList<Statement>()
        bodyStmts.add(tryStmt)

        return BlockStmt(bodyStmts)
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