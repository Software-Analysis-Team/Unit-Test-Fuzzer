package com.github.dmitrypogrebnoy.unittestfuzzer.services

import com.intellij.openapi.project.Project
import com.github.dmitrypogrebnoy.unittestfuzzer.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
