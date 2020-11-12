package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node

class JunitParser {

    fun parse(testingClassName: String, classSource: String): Pair<CompilationUnit, List<Node>> {
        val cu: CompilationUnit = StaticJavaParser.parse(classSource)
        val seedFinder = SeedFinder()
        val seeds = seedFinder.getSeeds(testingClassName, cu)

        return Pair(cu, seeds)
    }
}