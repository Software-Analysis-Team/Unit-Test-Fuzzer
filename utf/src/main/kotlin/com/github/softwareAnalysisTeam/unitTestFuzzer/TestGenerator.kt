package com.github.softwareAnalysisTeam.unitTestFuzzer

interface TestGenerator {
    fun getTests(testClassName: String, outputDir: String, timeout: Int): List<String>
}