package com.github.softwareAnalysisTeam.unitTestFuzzer

interface TestGenerator {
    fun getTests(testClassName: String, projectCP: String): List<String>
}