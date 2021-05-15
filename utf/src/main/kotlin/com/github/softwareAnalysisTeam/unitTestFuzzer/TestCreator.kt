package com.github.softwareAnalysisTeam.unitTestFuzzer

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
import com.github.javaparser.ast.type.Type
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.nio.charset.StandardCharsets.UTF_8

const val REGRESSION_CLASS_NAME = "RegressionClass"

class TestCreator {
    companion object {
        fun createTest(
            originalTest: CompilationUnit,
            testToConstruct: CompilationUnit,
            placesForNewValues: Map<MethodDeclaration, List<Expression>>,
            generatedValues: Map<String, List<String>>,
            outputDir: String,
            packageName: String?,
            cp: String,
            index: Int
        ) {
            val listWithModifiedTests: MutableList<MethodDeclaration> = mutableListOf()
            if (generatedValues.isNotEmpty()) {
                for (method in placesForNewValues.keys) {
                    val valuePlaces = placesForNewValues[method]
                    val values = generatedValues[method.nameAsString]

                    if (values != null && valuePlaces != null) {
                        val numberOfPlacesInMethod = valuePlaces.size

                        for (i in 0..(values.size - numberOfPlacesInMethod) step numberOfPlacesInMethod) {
                            for (j in 0 until numberOfPlacesInMethod) {
                                val parent = valuePlaces[j].parentNode.get() as Expression
                                try {
                                    valuePlaces[j].replaceWithNewValue(values[i + j])
                                } catch (e: Exception) {
                                    logger.debug("Failed to replace old value with a new one")
                                    logger.debug(e.stackTraceToString())
                                }
                                if (parent.isUnaryExpr) {
                                    parent.replace(valuePlaces[j])
                                }
                            }
                            listWithModifiedTests.add(
                                method.clone().setName("${method.name}_${i / numberOfPlacesInMethod}")
                            )
                        }
                    }
                }

                val regressionValuesFilePath = "$outputDir/regressionValues"

                val fileToRun: CompilationUnit
                val mapOfMethodVariables: MutableMap<String, MutableMap<String, Type>>

                constructRegressionFileAndGetVariables(
                    packageName,
                    testToConstruct,
                    listWithModifiedTests,
                    regressionValuesFilePath
                ).also {
                    fileToRun = it.first
                    mapOfMethodVariables = it.second
                }

                var fileForClassToRun: File? = null
                try {
                    fileForClassToRun = File("$outputDir/$REGRESSION_CLASS_NAME.java")
                    fileForClassToRun.createNewFile()
                    fileForClassToRun.writeText(fileToRun.toString())
                } catch (e: Exception) {
                    logger.error(e.stackTraceToString())
                }

                CommandExecutor.execute("javac -encoding UTF-8 -classpath $cp $fileForClassToRun", outputDir)

                if (packageName != null) {
                    CommandExecutor.execute("java -classpath $cp $packageName.$REGRESSION_CLASS_NAME", outputDir)
                } else {
                    CommandExecutor.execute("java -classpath $cp $REGRESSION_CLASS_NAME", outputDir)
                }

                try {
                    fileForClassToRun?.delete()
                    File("$outputDir/$REGRESSION_CLASS_NAME.class").delete()
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


                    val iterate = listWithModifiedTests.listIterator()
                    while (iterate.hasNext()) {
                        val method = iterate.next()
                        val asserts = method.collectAsserts()
                        val resultOfRun = objectInputStream.readObject().toString()

                        if (resultOfRun == "OK") {
                            if (!mapOfMethodVariables[method.nameAsString].isNullOrEmpty()) {
                                for (variable in mapOfMethodVariables[method.nameAsString]!!) {
                                    val regressionValue = objectInputStream.readObject().toString()

                                    if (!asserts.isNullOrEmpty()) {

                                        asserts.forEach { assert ->
                                            if (assert.toString().contains(variable.key)) {
                                                assert.setName("assertEquals")
                                                assert.arguments.clear()
                                                assert.addArgument(NameExpr(regressionValue))
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
                        } else {
                            iterate.remove()
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e.stackTraceToString())
                } finally {
                    objectInputStream?.close()
                    fileInputStream?.close()
                    regressionValuesFile.delete()
                }
            } else {
                logger.debug("No values were generated by fuzzer")
            }

            try {
                val modifiedTest = CompilationUnit()
                val modifiedTestClass = modifiedTest.addClass("ModifiedTest$index", Modifier.Keyword.PUBLIC)

                if (packageName != null) {
                    modifiedTest.setPackageDeclaration(packageName)
                }

                modifiedTest.imports = originalTest.imports
                val createdTestClassFile = File("$outputDir/${modifiedTestClass.name}.java")
                createdTestClassFile.createNewFile()


                for (newTest in listWithModifiedTests) {
                    modifiedTestClass.addMember(newTest)
                }

                logger.debug("New tests created\n: $listWithModifiedTests")

                createdTestClassFile.writeText(modifiedTest.toString(), UTF_8)
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }
        }

        private fun constructRegressionFileAndGetVariables(
            packageName: String?,
            testToConstruct: CompilationUnit,
            listWithModifiedTests: MutableList<MethodDeclaration>,
            regressionValuesFilePath: String
        ): Pair<CompilationUnit, MutableMap<String, MutableMap<String, Type>>> {
            val fileToRun = CompilationUnit()

            if (packageName != null) {
                fileToRun.setPackageDeclaration(packageName)
            }

            fileToRun.addImport("java.util.*")
            fileToRun.addImport("java.io.*")

            val classToRun = fileToRun.addClass(REGRESSION_CLASS_NAME, Modifier.Keyword.PUBLIC)

            testToConstruct.walk(FieldDeclaration::class.java) {
                classToRun.addMember(it)
            }

            val methods = mutableListOf<String>()

            listWithModifiedTests.forEach {
                val methodToAdd = it.clone()
                methodToAdd.addModifier(Modifier.Keyword.STATIC)
                methodToAdd.addParameter(
                    Parameter(
                        ClassOrInterfaceType("ObjectOutputStream"),
                        "objectOutputStream"
                    )
                )

                classToRun.addMember(methodToAdd)
                methods.add(it.nameAsString)
            }
            fileToRun.removeAsserts()

            fileToRun.walk(AnnotationExpr::class.java) {
                it.removeForced()
            }
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

                    for (statement in methodBody.statements) {
                        blockStmt.addStatement(statement)
                    }

                    blockStmt.addStatement("objectOutputStream.writeObject(\"OK\");")

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
                    catchStmt.addStatement("System.out.println(e);")
                    catchStmt.addStatement("objectOutputStream.writeObject(\"Exception\");")
                    catchClause.body = catchStmt
                    val stmts: NodeList<CatchClause> = NodeList()
                    stmts.add(catchClause)
                    tryStmt.catchClauses = stmts

                    val updatedBody = BlockStmt()
                    updatedBody.addStatement(tryStmt)
                    methodDecl.body.get().replace(updatedBody)
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

            return Pair(fileToRun, mapOfMethodVariables)
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