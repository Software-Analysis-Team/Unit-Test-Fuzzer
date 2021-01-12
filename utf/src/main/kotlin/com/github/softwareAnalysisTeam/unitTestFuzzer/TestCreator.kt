package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.Serializable

class TestCreator {
    companion object {
        fun createTest(
            originalTest: CompilationUnit,
            testToConstruct: CompilationUnit,
            placesForNewValues: Map<String, List<Expression>>,
            generatedValues: Map<String, List<String>>,
            projectPath: String
        ) {
            val resourcePath =
                projectPath + File.separator + "src" + File.separator + "main" + File.separator + "resources"
            val libsPath = projectPath + File.separator + "libs"

            for (method in placesForNewValues.keys) {
                val valuePlaces = placesForNewValues[method]!!
                val values = generatedValues[method]

                for (i in valuePlaces.indices) {

                    // todo: add the same tests with another generated values
                    if (values != null) {
                        if (i < values.size)
                            valuePlaces[i].replaceWithNewValue(values[i])
                    } else {
                        break;
                    }
                }
            }

            val regressionClassName = "RegressionClass"
            val fileToRun = CompilationUnit()

            fileToRun.addImport("java.util.*")
            fileToRun.addImport("java.io.*")

            val classToRun =  fileToRun.addClass(regressionClassName, Modifier.Keyword.PUBLIC)

            // todo: add the same instead of hardcoding "debug = false" in JQFZestFuzzer
            testToConstruct.walk(FieldDeclaration::class.java) {
                classToRun.addMember(it)
            }


            val methods = mutableListOf<String>()
            testToConstruct.walk(MethodDeclaration::class.java) {
                val methodToAdd = it.clone()
                methodToAdd.addModifier(Modifier.Keyword.STATIC)

                classToRun.addMember(methodToAdd)
                methods.add(it.nameAsString)
            }

            fileToRun.removeAsserts()

            fileToRun.walk(AnnotationExpr::class.java) {
                it.removeForced()
            }

            val regressionValuesFilePath = "$resourcePath/regressionValues"

            fileToRun.walk(MethodDeclaration::class.java) { methodDecl ->
                val listOfVariables = mutableListOf<String>()
                methodDecl.walk(VariableDeclarationExpr::class.java) { varDecl ->
                    varDecl.variables.forEach {
                        listOfVariables.add(it.nameAsString)
                    }
                }

                if (methodDecl.body.isPresent) {
                    val methodBody = methodDecl.body.get()

                    val tryStmt = TryStmt()
                    val blockStmt = BlockStmt()
                    blockStmt.addStatement("File file = new File(\"$regressionValuesFilePath\");")
                    blockStmt.addStatement("file.createNewFile();")
                    blockStmt.addStatement("OutputStream fileOutputStream = new FileOutputStream(file, true);")
                    blockStmt.addStatement("ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);")

                    for (variableName in listOfVariables) {
                        blockStmt.addStatement("if ($variableName instanceof Serializable) objectOutputStream.writeObject(${variableName});")
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

                    methodBody.addStatement(tryStmt)
                }
            }

            val mainMethod = classToRun.addMethod("main", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
            mainMethod.addParameter(Parameter(ArrayType(ClassOrInterfaceType("String")), "args"))
            mainMethod.addThrownException(ClassOrInterfaceType("Throwable"))
            val mainBody = mainMethod.createBody()

            for (method in methods) {
                mainBody.addStatement("$method();")
            }

            try {
                val fileForClassToRun = File("$resourcePath/$regressionClassName.java")
                fileForClassToRun.createNewFile()
                fileForClassToRun.writeText(fileToRun.toString())
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }


            CommandExecutor.execute("javac $regressionClassName.java", resourcePath)
            CommandExecutor.execute("java $regressionClassName", resourcePath)

            val listOfRegressionValues = mutableListOf<String>()
            val regressionValuesFile = File(regressionValuesFilePath)

            var fileInputStream: FileInputStream? = null
            var objectInputStream: ObjectInputStream? = null

            try {
                fileInputStream =
                    FileInputStream(regressionValuesFile)
                objectInputStream = ObjectInputStream(fileInputStream)

                while (true) {
                    listOfRegressionValues.add(objectInputStream.readObject().toString())
                }
            } catch (e: EOFException) {
                // todo: find a better way to stop reading from file
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            } finally {
                objectInputStream?.close()
                fileInputStream?.close()
            }

            // todo: modify asserts

            val asserts = testToConstruct.collectAsserts()
            val mapWithMethodAndNeededVariableName = mutableMapOf<String, MutableList<String>>()

            for (method in asserts.keys) {
                mapWithMethodAndNeededVariableName[method] = mutableListOf()

                if (!asserts[method].isNullOrEmpty()) {
                    for (assert in asserts[method]!!) {
                        assert.arguments.forEach { arg ->
                            arg.walk(SimpleName::class.java) {
                                mapWithMethodAndNeededVariableName[method]!!.add(it.asString())
                            }
                        }
                    }
                }
            }


            // todo: write created test to file

            // for method in originalTests
            // write to file method

            // write new methods
        }

        private fun Expression.replaceWithNewValue(newValue: String) {
            when (this) {
                is BooleanLiteralExpr -> this.setValue(newValue.toBoolean())
                is IntegerLiteralExpr -> this.setInt(Integer.parseInt(newValue))
                is LongLiteralExpr -> this.setLong(newValue.toLong())
                is DoubleLiteralExpr -> this.setDouble(newValue.toDouble())
                is CharLiteralExpr -> this.setChar(newValue.single())
                is StringLiteralExpr -> this.setString(newValue)
                else -> throw Exception("Unknown type of expression argument $this.")
            }
        }
    }
}