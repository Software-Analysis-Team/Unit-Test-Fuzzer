package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.stmt.TryStmt

fun CompilationUnit.removeTryCatchBlocks() {
    this.walk(TryStmt::class.java) { tryStmt ->
        tryStmt.removeForced()
    }
}

fun CompilationUnit.removeAsserts() {
    this.walk(MethodCallExpr::class.java) { methodCallExpr ->
        if (isAssert(methodCallExpr)) {
            methodCallExpr.removeForced()
        }
    }
}

fun CompilationUnit.collectAsserts(): Map<String, List<MethodCallExpr>> {
    val map = mutableMapOf<String, MutableList<MethodCallExpr>>()

    this.walk(MethodDeclaration::class.java) { testMethodDeclaration ->
        map[testMethodDeclaration.nameAsString] = mutableListOf()

        testMethodDeclaration.walk(MethodCallExpr::class.java) { methodCallExpr ->
            if (isAssert(methodCallExpr)) {
                map[testMethodDeclaration.nameAsString]!!.add(methodCallExpr)
            }
        }
    }

    return map
}

private fun isAssert(methodCallExpr: MethodCallExpr): Boolean {
    return methodCallExpr.nameAsString.contains("assertEquals")
            || methodCallExpr.toString().contains("org.junit.Assert.")
}