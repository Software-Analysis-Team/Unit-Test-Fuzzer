package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.Expression

class TestParser {

    companion object {
        fun parse(testingClassName: String, classSource: String): Pair<CompilationUnit, Map<String, List<Expression>>> {
            val cu: CompilationUnit = StaticJavaParser.parse(classSource)
            val seeds = SeedFinder.getSeeds(testingClassName, cu)

            return Pair(cu, seeds)
        }
    }
}