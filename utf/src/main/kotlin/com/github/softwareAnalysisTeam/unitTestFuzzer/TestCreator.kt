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
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream

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

            testToConstruct.walk(FieldDeclaration::class.java) {
                classToRun.addMember(it)
            }

            val methods = mutableListOf<String>()
            testToConstruct.walk(MethodDeclaration::class.java) {
                val methodToAdd = it.clone()
                methodToAdd.addModifier(Modifier.Keyword.STATIC)
                methodToAdd.addParameter(Parameter(ClassOrInterfaceType("ObjectOutputStream"), "objectOutputStream"))

                classToRun.addMember(methodToAdd)
                methods.add(it.nameAsString)
            }

            fileToRun.removeAsserts()

            fileToRun.walk(AnnotationExpr::class.java) {
                it.removeForced()
            }

            val regressionValuesFilePath = "$resourcePath/regressionValues"
            classToRun.addMember(FieldDeclaration())

            // reduce it?
            val mapOfMethodVariables = mutableMapOf<String, MutableList<String>>()

            fileToRun.walk(MethodDeclaration::class.java) { methodDecl ->
                val curList = mutableListOf<String>()
                mapOfMethodVariables[methodDecl.nameAsString] = curList
                methodDecl.walk(VariableDeclarationExpr::class.java) { varDecl ->
                    varDecl.variables.forEach {
                        if (it.type.isPrimitiveType || it.typeAsString == "String") {
                            curList.add(it.nameAsString)
                        }
                    }
                }

                if (methodDecl.body.isPresent) {
                    val methodBody = methodDecl.body.get()

                    val tryStmt = TryStmt()
                    val blockStmt = BlockStmt()

                    for (variableName in curList) {
                        blockStmt.addStatement("objectOutputStream.writeObject(${variableName});")
                    }

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

            mainBody.addStatement("File file = new File(\"$regressionValuesFilePath\");")
            mainBody.addStatement("file.createNewFile();")
            mainBody.addStatement("OutputStream fileOutputStream = new FileOutputStream(file, true);")
            mainBody.addStatement("ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);")

            for (method in methods) {
                mainBody.addStatement("$method(objectOutputStream);")
            }

            mainBody.addStatement("objectOutputStream.close();")
            mainBody.addStatement("fileOutputStream.close();")

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

//            try {
                fileInputStream =
                    FileInputStream(regressionValuesFile)
                objectInputStream = ObjectInputStream(fileInputStream)

//                while (true) {
//                    listOfRegressionValues.add(objectInputStream.readObject().toString())
//                }
//            } catch (e: EOFException) {
//                // todo: find a better way to stop reading from file
//            } catch (e: Exception) {
//                logger.error(e.stackTraceToString())
//            } finally {
//                objectInputStream?.close()
//                fileInputStream?.close()
//            }

            // todo: modify asserts

            val asserts = testToConstruct.collectAsserts()
            for (method in asserts.keys) {
                if (!mapOfMethodVariables[method].isNullOrEmpty() && !asserts[method].isNullOrEmpty()) {
                    for (variable in mapOfMethodVariables[method]!!) {
                        asserts[method]!!.forEach { assert ->
                            if (assert.toString().contains(variable)) {
                                //val newAssert = assert.clone()
                                //assert.setName("assertEquals")
                                assert.setArgument(0, StringLiteralExpr(objectInputStream.readObject().toString()))
                                assert.setArgument(1, StringLiteralExpr(variable))
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