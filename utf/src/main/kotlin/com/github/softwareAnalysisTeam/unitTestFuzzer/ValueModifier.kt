package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExpressionStmt

class ValueModifier {

    fun visit(cu: CompilationUnit): List<Node> {
        val seeds = mutableListOf<Node>()

        cu.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
            testMethodDeclaration.walk(ExpressionStmt::class.java) { exprStmt ->
                val expr = exprStmt.expression

                expr.ifVariableDeclarationExpr { varDeclExpr ->
                    varDeclExpr.variables.forEach { varDecl ->
                        varDecl.initializer.ifPresent { initializer ->

                            initializer.ifObjectCreationExpr { objCreationExpr ->
                                objCreationExpr.arguments.forEach { argOfConstructor ->
                                    collectAndReplace(argOfConstructor, seeds)
                                }
                            }

                            // do we really need it?
                            initializer.ifMethodCallExpr { methodCallExpr ->
                                methodCallExpr.arguments.forEach { argOfMethod ->
                                    collectAndReplace(argOfMethod, seeds)
                                }
                            }
                        }
                    }
                }

                expr.ifMethodCallExpr { methodCallExpr ->
                    methodCallExpr.arguments.forEach { argOfMethod ->
                        collectAndReplace(argOfMethod, seeds)
                    }
                }
            }
        }

        return seeds
    }
}

fun findValue(node: Node): Node {
    val nodes = node.childNodes

    if (nodes.isNotEmpty()) {
        val curExpr = node as Expression
        if (curExpr.isUnaryExpr && (curExpr.asUnaryExpr().operator == UnaryExpr.Operator.MINUS)) {
            return node
        }
        return findValue(nodes.last() as Node)
    }

    return node
}

fun collectAndReplace(node: Node, seeds: MutableList<Node>) {
    findValue(node).also {
        if (node !is NameExpr) {
            seeds.add(it)
            it.replace(StringLiteralExpr("###"))
        }
    }
}