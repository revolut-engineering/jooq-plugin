package com.revolut.jooq

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

open class JooqDockerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create("jooq", JooqExtension::class.java, project.name)

        // Create jdbc configuration
        val jdbcConfiguration = project.configurations.create("jdbc")

        val generateJooqTask = project.tasks.register("generateJooqClasses", GenerateJooqClassesTask::class.java) {
            group = "jooq"
        }

        // Make jdbc configuration inherit versions from implementation when Java plugin is applied
        project.plugins.withType(JavaPlugin::class.java) {
            project.configurations.findByName("implementation")?.let { implementation ->
                jdbcConfiguration.extendsFrom(implementation)
            }

            project.extensions.configure(JavaPluginExtension::class.java) {
                sourceSets.named(MAIN_SOURCE_SET_NAME) {
                    java {
                        srcDir(generateJooqTask.flatMap { it.outputDirectory })
                    }
                }
            }
            project.tasks.named("compileJava") {
                dependsOn(generateJooqTask)
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.extensions.configure(JavaPluginExtension::class.java) {
                sourceSets.named(MAIN_SOURCE_SET_NAME) {
                    java {
                        srcDir(generateJooqTask.flatMap { it.outputDirectory })
                    }
                }
            }
            project.tasks.named("compileKotlin") {
                dependsOn(generateJooqTask)
            }
        }
    }
}