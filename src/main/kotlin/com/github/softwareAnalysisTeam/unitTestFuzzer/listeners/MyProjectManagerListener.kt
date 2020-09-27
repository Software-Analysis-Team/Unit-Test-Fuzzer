package com.github.softwareAnalysisTeam.unitTestFuzzer.listeners

import com.github.softwareAnalysisTeam.unitTestFuzzer.services.MyProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class MyProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.getService(MyProjectService::class.java)
    }
}
