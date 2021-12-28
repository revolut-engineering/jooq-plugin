package com.revolut.jooq

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

open class JooqDockerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create("jooq", JooqExtension::class.java, project.name)
        val configuration = project.configurations.create("jdbc")
        addJooqCodegenDependencyToPluginRuntime(project, configuration)
        project.tasks.create("generateJooqClasses", GenerateJooqClassesTask::class.java) {
            group = "jooq"
        }
    }

    private fun addJooqCodegenDependencyToPluginRuntime(project: Project, configuration: Configuration) {
        project.dependencies.add(configuration.name, project.provider {
            findJooqVersionOnCompileClasspath(project)
                    ?.let { "org.jooq:jooq-codegen:$it" }
                    ?: throw IllegalStateException("Unable to resolve jooq version. Please add jooq to your classpath")
        })
    }

    private fun findJooqVersionOnCompileClasspath(project: Project) =
            project.configurations.getByName("compileClasspath")
                    .resolvedConfiguration
                    .resolvedArtifacts
                    .map { it.moduleVersion.id }
                    .find { it.name == "jooq" }
                    ?.version
}