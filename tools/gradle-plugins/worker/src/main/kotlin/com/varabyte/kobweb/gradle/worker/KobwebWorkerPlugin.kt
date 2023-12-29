package com.varabyte.kobweb.gradle.worker

import com.varabyte.kobweb.ProcessorMode
import com.varabyte.kobweb.gradle.core.KobwebCorePlugin
import com.varabyte.kobweb.gradle.core.kmp.JsTarget
import com.varabyte.kobweb.gradle.core.kmp.buildTargets
import com.varabyte.kobweb.gradle.core.ksp.applyKspPlugin
import com.varabyte.kobweb.gradle.core.ksp.setupKspJs
import com.varabyte.kobweb.gradle.core.util.configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

class KobwebWorkerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("HELLO FROM WORKER PLUGIN!!!")

        project.pluginManager.apply(KobwebCorePlugin::class.java)
        project.applyKspPlugin()
        project.tasks.withType<KotlinWebpack>().configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode()

        project.buildTargets.withType<KotlinJsIrTarget>().configureEach {
            val jsTarget = JsTarget(this)
            project.setupKspJs(jsTarget, ProcessorMode.WORKER)
        }
    }
}
