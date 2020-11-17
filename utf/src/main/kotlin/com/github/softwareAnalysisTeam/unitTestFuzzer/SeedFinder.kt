package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExpressionStmt

class SeedFinder {

    fun getSeeds(testingClassName: String, cu: CompilationUnit): List<Node> {
        val seeds = mutableListOf<Node>()

        cu.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
            testMethodDeclaration.walk(ExpressionStmt::class.java) { exprStmt ->
                val expr = exprStmt.expression

                expr.ifVariableDeclarationExpr { varDeclExpr ->
                    varDeclExpr.variables.forEach { varDecl ->
                        varDecl.initializer.ifPresent { initializer ->

                            initializer.ifObjectCreationExpr { objCreationExpr ->
                                if (objCreationExpr.toString().contains(testingClassName)) {
                                    objCreationExpr.arguments.forEach { argOfConstructor ->
                                        collectAndReplace(argOfConstructor, seeds)
                                    }
                                }
                            }
                            // do we need it?
                            initializer.ifMethodCallExpr { methodCallExpr ->
                                findValuesInMethod(testingClassName, testMethodDeclaration, methodCallExpr, seeds)
                            }
                        }
                    }
                }

                expr.ifMethodCallExpr { methodCallExpr ->
                    findValuesInMethod(testingClassName, testMethodDeclaration, methodCallExpr, seeds)
                }
            }
        }
        return seeds
    }

    private fun findValuesInMethod(
        testingClassName: String,
        methodDeclaration: MethodDeclaration,
        methodCallExpr: MethodCallExpr,
        seeds: MutableList<Node>
    ) {
        val methodCallExprString = methodCallExpr.toString()

        if (methodCallExprString.startsWith("$testingClassName.") ||
            findImplicitArgTypeName(methodDeclaration, methodCallExprString) == testingClassName
        ) {
            methodCallExpr.arguments.forEach { argOfMethod ->
                collectAndReplace(argOfMethod, seeds)
            }
        }
    }


    private fun findValueInArgument(node: Node): Node {
        val nodes = node.childNodes

        if (nodes.isNotEmpty()) {
            val curExpr = node as Expression
            if (curExpr.isUnaryExpr && (curExpr.asUnaryExpr().operator == UnaryExpr.Operator.MINUS)) {
                return node
            }
            return findValueInArgument(nodes.last() as Node)
        }

        return node
    }

    private fun collectAndReplace(node: Node, seeds: MutableList<Node>) {
        findValueInArgument(node).also {
            if (node !is NameExpr) {
                seeds.add(it)
                it.replace(StringLiteralExpr("###"))
            }
        }
    }

    private fun findImplicitArgTypeName(methodDeclaration: MethodDeclaration, methodCallExprString: String): String {
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