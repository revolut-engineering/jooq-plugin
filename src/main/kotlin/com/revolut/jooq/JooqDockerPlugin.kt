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

        val allJooqTasks = project.tasks.withType(GenerateJooqClassesTask::class.java)

        // Make jdbc configuration inherit versions from implementation when Java plugin is applied
        project.plugins.withType(JavaPlugin::class.java) {
            project.configurations.findByName("implementation")?.let { implementation ->
                jdbcConfiguration.extendsFrom(implementation)
            }

            allJooqTasks.configureEach {
                project.extensions.configure(JavaPluginExtension::class.java) {
                    sourceSets.named(MAIN_SOURCE_SET_NAME) {
                        java {
                            srcDir(this@configureEach.outputDirectory)
                        }
                    }
                }
            }
            project.tasks.named("compileJava") {
                dependsOn(allJooqTasks)
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            allJooqTasks.configureEach {
                project.extensions.configure(JavaPluginExtension::class.java) {
                    sourceSets.named(MAIN_SOURCE_SET_NAME) {
                        java {
                            srcDir(this@configureEach.outputDirectory)
                        }
                    }
                }
            }
            project.tasks.named("compileKotlin") {
                dependsOn(allJooqTasks)
            }
        }
    }
}