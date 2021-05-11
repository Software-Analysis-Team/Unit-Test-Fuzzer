package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExpressionStmt

internal class SeedFinder {
    companion object {
        fun getSeeds(testingClassName: String, cu: CompilationUnit): Map<MethodDeclaration, List<Expression>> {
            val map = mutableMapOf<MethodDeclaration, MutableList<Expression>>()

            cu.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
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
            map: MutableMap<MethodDeclaration, MutableList<Expression>>
        ) {
            val methodCallExprString = methodCallExpr.toString()
            val className = testingClassName.split(".").last()
            val implicitArgType = findImplicitArgTypeName(methodDeclaration, methodCallExprString)

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


        private fun findValueInArgument(node: Node): Node {
            val nodes = node.childNodes

            if (nodes.isNotEmpty()) {
                return findValueInArgument(nodes.last() as Node)
            }
            return node
        }

        private fun collect(
            node: Node,
            testMethodDeclaration: MethodDeclaration,
            map: MutableMap<MethodDeclaration, MutableList<Expression>>
        ) {

            findValueInArgument(node).also {
                if (node !is NameExpr && it is Expression) {
                    if (map[testMethodDeclaration] != null) {
                        map[testMethodDeclaration]!!.add(it)
                    } else {
                        map[testMethodDeclaration] = mutableListOf()
                        map[testMethodDeclaration]!!.add(it)
                    }
                }
            }
        }

        private fun findImplicitArgTypeName(
            methodDeclaration: MethodDeclaration,
            methodCallExprString: String
        ): String {
            var result = ""

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