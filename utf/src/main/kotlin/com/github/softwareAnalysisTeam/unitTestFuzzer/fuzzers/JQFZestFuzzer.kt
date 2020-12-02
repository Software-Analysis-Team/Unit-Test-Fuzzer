package com.github.softwareAnalysisTeam.unitTestFuzzer.fuzzers

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.Type
import com.github.softwareAnalysisTeam.unitTestFuzzer.CommandExecutor
import com.github.softwareAnalysisTeam.unitTestFuzzer.Fuzzer
import java.io.File
import java.nio.file.Paths
import java.util.*


class JQFZestFuzzer : Fuzzer {
    // todo: fix paths
    private val commandToCompile: String
    private val commandToRun: String
    private val projectDir = Paths.get("").toAbsolutePath().toString()
    private var classPaths: String

    constructor() {
        val classPathsFileName = UUID.randomUUID().toString() + ".txt"
        val fileForPaths = File(projectDir + File.separator + classPathsFileName)
        fileForPaths.createNewFile()
        CommandExecutor.execute("libs/jqf/scripts/classpath.sh", projectDir, fileForPaths)
        classPaths = fileForPaths.readText()
        fileForPaths.delete()

        commandToCompile = "javac -cp .:$classPaths "
        commandToRun = "timeout 15s libs/jqf/bin/jqf-zest -c .:$classPaths "
    }

    override fun getValues(seeds: List<Expression>, cu: CompilationUnit): List<List<Expression>> {
        val resourcesDir = Paths.get(projectDir, "src", "main", "resources").toString()
        val fileToFuzz = File(resourcesDir + File.separator + "ClassToFuzz.java")
        fileToFuzz.writeText(constructClassToFuzz(seeds).toString())

        CommandExecutor.execute(commandToCompile + "ClassToFuzz.java", resourcesDir)

        return mutableListOf()

    }

    private fun constructClassToFuzz(seeds: List<Expression>): CompilationUnit {
        val fileToFuzz = CompilationUnit()
        fileToFuzz.addImport("java.util.*")
        fileToFuzz.addImport("java.io.*")

        fileToFuzz.addImport("org.junit.runner.RunWith")
        fileToFuzz.addImport("com.pholser.junit.quickcheck.*")
        fileToFuzz.addImport("com.pholser.junit.quickcheck.generator.*")
        fileToFuzz.addImport("edu.berkeley.cs.jqf.fuzz.*")

        val classToFuzz = fileToFuzz.addClass("ClassToFuzz", Modifier.Keyword.PUBLIC)
        val classAnnotation = SingleMemberAnnotationExpr().setMemberValue(NameExpr("JQF.class")).setName("RunWith")
        classToFuzz.addAnnotation(classAnnotation)

        val methodToFuzz = classToFuzz.addMethod("MethodToFuzz", Modifier.Keyword.PUBLIC)
        val methodAnnotation = MarkerAnnotationExpr().setName("Fuzz")
        methodToFuzz.addAnnotation(methodAnnotation)

        for (i in seeds.indices) {
            methodToFuzz.addParameter(Parameter(seeds[i].getType(), "methodToFuzzArgument$i"))
        }

        val ts = TryStmt()
        val bs = BlockStmt()
        /// try block stmts
        bs.addStatement("File file = new File(\"generatedValues.txt\");")
        bs.addStatement("file.createNewFile();")
        bs.addStatement("FileWriter writer = new FileWriter(file, true);")
        bs.addStatement("writer.write(\"New generated value for 1st arg: \");")
        //bs.addStatement("writer.write(\"New generated value for 1st arg: \" + methodToFuzz.parameters[0]);")
        bs.addStatement("writer.close();")
        ts.tryBlock = bs

        val cc = CatchClause().setParameter(Parameter(ClassOrInterfaceType("Exception"), "e"))
        val cb = BlockStmt()
        /// catch block stmts
        cb.addStatement(StaticJavaParser.parseStatement("e.getStackTrace();"))
        cc.body = cb

        val sts: NodeList<CatchClause> = NodeList()
        sts.add(cc)
        ts.catchClauses = sts
        val bodyStmts = NodeList<Statement>()
        bodyStmts.add(ts)
        methodToFuzz.setBody(BlockStmt(bodyStmts))

        return fileToFuzz
    }

    private fun Expression.getType(): Type {
        return when (this) {
            is UnaryExpr -> (this.childNodes[0] as Expression).getType()
            is BooleanLiteralExpr -> PrimitiveType.booleanType()
            is IntegerLiteralExpr -> PrimitiveType.intType()
            is LongLiteralExpr -> PrimitiveType.longType()
            is DoubleLiteralExpr -> PrimitiveType.doubleType()
            is CharLiteralExpr -> PrimitiveType.charType()
            is StringLiteralExpr -> ClassOrInterfaceType("String")
            else -> throw Exception("Unknown type of expression argument.")
        }
    }
}