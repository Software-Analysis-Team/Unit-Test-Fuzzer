package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.Expression

interface Fuzzer {
    fun getValues(seeds: List<Expression>, cu: CompilationUnit): List<List<Expression>>
}