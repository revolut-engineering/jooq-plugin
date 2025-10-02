package com.revolut.jooq

import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.util.GradleVersion

class MultiVersionCompatibilitySpec : FunctionalSpec() {
    @GradleMatrixTest
    fun `correctly builds and runs minimal project`(@TargetGradleVersion gradleVersion: GradleVersion) {
        // given
        buildFile.text =
            """
            plugins {
                id("java")
                id("com.revolut.jooq-docker")
            }

            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation("org.jooq:jooq:3.14.15")
                jdbc("org.postgresql:postgresql:42.2.5")
            }
            """.trimIndent()

        file("src/main/resources/db/migration/V01__init.sql").text =
            """
            create table foo
            (
                id   UUID primary key,
                data JSONB not null
            );
            """.trimIndent()

        // when
        val result = gradleVersion.run("build")

        // then
        result.task(":generateJooqClasses") shouldHaveOutcome SUCCESS
    }
}
