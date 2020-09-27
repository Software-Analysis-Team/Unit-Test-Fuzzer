package com.github.softwareAnalysisTeam.unitTestFuzzer.services

import com.github.softwareAnalysisTeam.unitTestFuzzer.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
