package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExpressionStmt

internal class SeedFinder {
    companion object {
        fun getSeeds(testingClassName: String, cu: CompilationUnit): Map<String, List<Expression>> {
            val map = mutableMapOf<String, MutableList<Expression>>()

            cu.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
                map[testMethodDeclaration.nameAsString] = mutableListOf()
                testMethodDeclaration.walk(ExpressionStmt::class.java) { exprStmt ->
                    val expr = exprStmt.expression

                    expr.ifVariableDeclarationExpr { varDeclExpr ->
                        varDeclExpr.variables.forEach { varDecl ->
                            varDecl.initializer.ifPresent { initializer ->

                                initializer.ifObjectCreationExpr { objCreationExpr ->
                                    if (objCreationExpr.toString().contains(testingClassName)) {
                                        objCreationExpr.arguments.forEach { argOfConstructor ->
                                            collect(argOfConstructor, testMethodDeclaration, map)
                                        }
                                    }
                                }
                                // do we need it?
                                initializer.ifMethodCallExpr { methodCallExpr ->
                                    findAndCollectValuesInMethod(
                                        testingClassName,
                                        testMethodDeclaration,
                                        methodCallExpr,
                                        map
                                    )
                                }
                            }
                        }
                    }

                    expr.ifMethodCallExpr { methodCallExpr ->
                        findAndCollectValuesInMethod(testingClassName, testMethodDeclaration, methodCallExpr, map)
                    }
                }
            }
            return map
        }

        private fun findAndCollectValuesInMethod(
            testingClassName: String,
            methodDeclaration: MethodDeclaration,
            methodCallExpr: MethodCallExpr,
            map: MutableMap<String, MutableList<Expression>>
        ) {
            val methodCallExprString = methodCallExpr.toString()
            val className = testingClassName.split(".").last()
            val implicitArgType = findImplicitArgTypeName(methodDeclaration, methodCallExprString)

            // todo: check if it works with all cases
            if (methodCallExprString.startsWith("$testingClassName.") ||
                methodCallExprString.startsWith("$className.") ||
                implicitArgType == testingClassName ||
                implicitArgType == className
            ) {
                methodCallExpr.arguments.forEach { argOfMethod ->
                    collect(argOfMethod, methodDeclaration, map)
                }
            }
        }


        private fun findValuesInArgument(node: Node): Node {
            val nodes = node.childNodes

            if (nodes.isNotEmpty()) {
                val curExpr = node as Expression

                // unbox digits from UnaryExpr
                // suppose, we have only unary minus as operator
                if (curExpr.isUnaryExpr && (curExpr.asUnaryExpr().operator == UnaryExpr.Operator.MINUS)) {
                    if (curExpr.childNodes.size == 1) {
                        return when (val boxedValue = curExpr.childNodes[0]) {
                            is IntegerLiteralExpr -> {
                                val unboxedIntExpr = IntegerLiteralExpr().setInt(-boxedValue.asInt())
                                node.replace(unboxedIntExpr)
                                unboxedIntExpr
                            }
                            is LongLiteralExpr -> {
                                val unboxedLongExpr = LongLiteralExpr().setLong(-boxedValue.asLong())
                                node.replace(unboxedLongExpr)
                                unboxedLongExpr
                            }
                            is DoubleLiteralExpr -> {
                                val unboxedLongExpr = DoubleLiteralExpr().setDouble(-boxedValue.asDouble())
                                node.replace(unboxedLongExpr)
                                unboxedLongExpr
                            }
                            else -> throw Exception("Used non-digital value with unary minus")
                        }
                    }
                    return node
                }

                return findValuesInArgument(nodes.last() as Node)
            }

            return node
        }

        private fun collect(
            node: Node,
            testMethodDeclaration: MethodDeclaration,
            map: MutableMap<String, MutableList<Expression>>
        ) {
            findValuesInArgument(node).also {
                if (node !is NameExpr) {
                    map[testMethodDeclaration.nameAsString]!!.add(it as Expression)
                }
            }
        }

        private fun findImplicitArgTypeName(
            methodDeclaration: MethodDeclaration,
            methodCallExprString: String
        ): String {
            var result = ""

            // suppose all needed declarations are in current test method
            methodDeclaration.walk(VariableDeclarationExpr::class.java) { variableDeclarationExpr ->
                val variableName = methodCallExprString.split(".")[0]
                variableDeclarationExpr.variables.forEach {
                    if (it.name.toString() == variableName) {
                        result = it.type.toString()
                    }
                }
            }
            return result
        }
    }
}