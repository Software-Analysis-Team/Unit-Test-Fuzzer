package com.github.softwareAnalysisTeam.unitTestFuzzer

interface TestGenerator {
    fun getTests(testClassName: String, classPath: String): List<String>
}