package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.Expression

interface Fuzzer {
    fun getValues(testingClassName: String, cu: CompilationUnit, seeds: Map<String, List<Expression>>, budgetPerMethod: Double): Map<String, List<String>>
}