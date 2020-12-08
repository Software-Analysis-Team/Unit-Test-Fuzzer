package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*

class TestCreator {
    companion object {
        fun createTest(cu: CompilationUnit, seeds: List<Expression>, generatedValues: List<String>) {
            for (i in seeds.indices) {
                seeds[i].replaceWithNewValue(generatedValues[i])
            }

            // todo: modify asserts

            // todo: write created test to file
        }

        fun Expression.replaceWithNewValue(newValue: String) {
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

        fun collectAndDeleteAsserts(cu: CompilationUnit): List<MethodCallExpr> {
            val listWithAsserts = mutableListOf<MethodCallExpr>()

            cu.walk(MethodDeclaration::class.java) { methodDecl ->
                if (methodDecl.body.isPresent) {
                    val body = methodDecl.body.get()
                    body.walk(MethodCallExpr::class.java) { methodCallExpr ->
                        // todo: replace with more intelligent way to find asserts
                        if (methodCallExpr.name.asString().toLowerCase().contains("assert")) {
                            listWithAsserts.add(methodCallExpr)
                            methodCallExpr.removeForced()
                        }
                    }
                }
            }

            return listWithAsserts
        }
    }
}