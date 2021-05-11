package com.github.softwareAnalysisTeam.unitTestFuzzer.action

import com.github.softwareAnalysisTeam.unitTestFuzzer.JarExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.io.File

class UTFAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val nav = event.getData(CommonDataKeys.NAVIGATABLE)
        if (nav != null && nav.toString().contains("PsiClass:") && project != null && project.basePath != null) {
            val className = nav.toString().removePrefix("PsiClass:")
            val args: MutableList<String> = ArrayList()

            args.add("-classpath")
            args.add("." + File.separator + "build" + File.separator + "classes" + File.separator + "java" + File.separator + "main"
                    + File.pathSeparator + "." + File.separator + "libs" + File.separator + "myRandoop-all-4.2.4.jar")
            args.add("randoop.main.Main")
            args.add("gentests")
            args.add("--testclass=" + className)
            args.add("--junit-output-dir=." + File.separator + "src" + File.separator + "test" + File.separator + "java")
            args.add("--time-limit=5")

            val jarExecutor = JarExecutor()
            jarExecutor.executeJar(project.basePath!!, args)
        }
    }
}
