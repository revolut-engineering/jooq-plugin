package com.revolut.jooq

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

open class JooqDockerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        project.extensions.create("jooq", JooqExtension::class.java)
        project.configurations.create("jdbc")
        project.tasks.create("generateJooqClasses", GenerateJooqClassesTask::class.java) {
            group = "jooq"
        }
    }
}