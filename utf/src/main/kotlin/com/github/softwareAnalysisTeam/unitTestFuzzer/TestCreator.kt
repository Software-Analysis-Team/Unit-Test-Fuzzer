package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream

class TestCreator {
    companion object {
        fun createTest(
            numberOfTest: Int,
            originalTest: CompilationUnit,
            testToConstruct: CompilationUnit,
            placesForNewValues: Map<MethodDeclaration, List<Expression>>,
            generatedValues: Map<String, List<String>>,
            outputDir: String,
            cp: String
        ) {
            val listWithModifiedTests: MutableList<MethodDeclaration> = mutableListOf()

            for (method in placesForNewValues.keys) {
                val valuePlaces = placesForNewValues[method]
                val values = generatedValues[method.nameAsString]

                if (values != null && valuePlaces != null) {
                    val numberOfPlacesInMethod = valuePlaces.size

                    for (i in 0..(values.size - numberOfPlacesInMethod) step numberOfPlacesInMethod) {
                        for (j in 0 until numberOfPlacesInMethod) {
                            valuePlaces[j].replaceWithNewValue(values[i + j])
                        }
                        listWithModifiedTests.add(method.clone().setName("${method.name}_${i/numberOfPlacesInMethod}"))
                    }
                }
            }

            val regressionClassName = "RegressionClass$numberOfTest"
            val createdTestsClassName = "RegressionTest$numberOfTest"
            val fileToRun = CompilationUnit()

            fileToRun.addImport("java.util.*")
            fileToRun.addImport("java.io.*")

            val classToRun = fileToRun.addClass(regressionClassName, Modifier.Keyword.PUBLIC)

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

            val regressionValuesFilePath = "$outputDir/regressionValues"
            classToRun.addMember(FieldDeclaration())

            val mapOfMethodVariables = mutableMapOf<String, MutableMap<String, Type>>()

            fileToRun.walk(MethodDeclaration::class.java) { methodDecl ->
                val currentMap = mutableMapOf<String, Type>()
                mapOfMethodVariables[methodDecl.nameAsString] = currentMap
                methodDecl.walk(VariableDeclarationExpr::class.java) { varDecl ->
                    varDecl.variables.forEach {
                        if (it.type.isPrimitiveType || it.typeAsString == "String") {
                            currentMap[it.nameAsString] = it.type
                        }
                    }
                }

                if (methodDecl.body.isPresent) {
                    val methodBody = methodDecl.body.get()

                    val tryStmt = TryStmt()
                    val blockStmt = BlockStmt()

                    for (variableName in currentMap.keys) {
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

            var fileForClassToRun: File? = null
            try {
                fileForClassToRun = File("$outputDir/$regressionClassName.java")
                fileForClassToRun.createNewFile()
                fileForClassToRun.writeText(fileToRun.toString())
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }

            CommandExecutor.execute("javac -encoding UTF-8 -classpath $cp $fileForClassToRun", outputDir)
            CommandExecutor.execute("java -classpath $cp $regressionClassName", outputDir)

            try {
                fileForClassToRun?.delete()
                File("$outputDir/$regressionClassName.class").delete()
            } catch (e: IOException) {
                logger.error(e.stackTraceToString())
            }

            val regressionValuesFile = File(regressionValuesFilePath)

            var fileInputStream: FileInputStream? = null
            var objectInputStream: ObjectInputStream? = null

            try {
                fileInputStream =
                    FileInputStream(regressionValuesFile)
                objectInputStream = ObjectInputStream(fileInputStream)

                val asserts = testToConstruct.collectAsserts()
                for (method in asserts.keys) {
                    if (!mapOfMethodVariables[method].isNullOrEmpty() && !asserts[method].isNullOrEmpty()) {
                        for (variable in mapOfMethodVariables[method]!!) {
                            asserts[method]!!.forEach { assert ->
                                if (assert.toString().contains(variable.key)) {
                                    assert.setName("assertEquals")
                                    assert.arguments.clear()
                                    assert.addArgument(NameExpr(objectInputStream.readObject().toString()))
                                    assert.addArgument(NameExpr(variable.key))

                                    if (variable.value.asString().contains("double", true) ||
                                        variable.value.asString().contains("float", true)
                                    ) {
                                        assert.addArgument("0.01")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            } finally {
                objectInputStream?.close()
                fileInputStream?.close()
                regressionValuesFile.delete()
            }

            try {
                val createdTestClassFile = File("$outputDir/$createdTestsClassName.java")
                createdTestClassFile.createNewFile()

                originalTest.walk(ClassOrInterfaceDeclaration::class.java) { testClass ->
                    for (newTest in listWithModifiedTests) {
                        testClass.addMember(newTest)
                    }
                }

                createdTestClassFile.writeText(originalTest.toString())
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }
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