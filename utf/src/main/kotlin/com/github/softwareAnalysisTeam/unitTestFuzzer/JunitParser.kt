package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node

class JunitParser {

    fun parse(classSource: String): Pair<CompilationUnit, List<Node>> {
        val cu: CompilationUnit = StaticJavaParser.parse(classSource)
        val modifier = ValueModifier()
        val seeds = modifier.visit(cu)

        return Pair(cu, seeds)
    }
}