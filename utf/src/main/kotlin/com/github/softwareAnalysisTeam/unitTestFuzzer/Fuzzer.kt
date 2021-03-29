package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.Expression

interface Fuzzer {
    fun getValues(testingClassName: String, packageName: String?, cu: CompilationUnit, seeds: Map<MethodDeclaration, List<Expression>>, budgetPerMethod: Double): Map<String, List<String>>
}