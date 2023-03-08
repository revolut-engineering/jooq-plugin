import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import java.net.URI

plugins {
    `kotlin-dsl`
    groovy
    `maven-publish`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.14.0"
    if (System.getenv().containsKey("TRAVIS") || !System.getenv().containsKey("CI")) {
        jacoco
        id("pl.droidsonroids.jacoco.testkit") version "1.0.8"
    }
    id("com.github.ben-manes.versions").version("0.38.0")
}

repositories {
    mavenCentral()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "com.revolut.jooq"
version = "0.3.9"

gradlePlugin {
    plugins.create("jooqDockerPlugin") {
        id = "com.revolut.jooq-docker"
        implementationClass = "com.revolut.jooq.JooqDockerPlugin"
        version = project.version
    }
}

pluginBundle {
    website = "https://github.com/revolut-engineering/jooq-plugin"
    vcsUrl = "https://github.com/revolut-engineering/jooq-plugin"

    description = "Generates jOOQ classes using dockerized database"

    (plugins) {
        "jooqDockerPlugin" {
            displayName = "jOOQ Docker Plugin"
            tags = listOf("jooq", "docker", "db")
            version = project.version.toString()
        }
    }
}

publishing {
    repositories {
        val snapshotRepository by extra { project.findProperty("snapshotRepository")?.toString() ?: "" }
        val releaseRepository by extra { project.findProperty("releaseRepository")?.toString() ?: "" }
        maven {
            url = URI(if (project.version.toString().endsWith("-SNAPSHOT")) snapshotRepository else releaseRepository)
        }
    }
}

tasks {
    withType<Test>().configureEach {
        if (JavaVersion.current().isJava9Compatible) {
            jvmArgs("--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED")
            jvmArgs("--illegal-access=deny")
        }
        testLogging {
            events(PASSED, FAILED)
            showExceptions = true
            showStackTraces = true
            showCauses = true
            exceptionFormat = FULL
        }
    }

    withType<JacocoReport> {
        reports {
            xml.isEnabled = true
            html.isEnabled = false
        }
        setDependsOn(withType<Test>())
    }

    dependencyUpdates {
        resolutionStrategy {
            componentSelection {
                all {
                    val rejected = listOf("alpha", "beta", "b", "rc", "cr", "m", "preview")
                        .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-]?.*") }
                        .any { it.matches(candidate.version) }
                    if (rejected) {
                        reject("Release candidate")
                    }
                }
            }
        }
        gradleReleaseChannel = CURRENT.id
    }

    wrapper {
        gradleVersion = "6.7"
        distributionType = ALL
    }
}

afterEvaluate {
    tasks.withType<JacocoReport> {
        classDirectories.setFrom(classDirectories.files.map {
            fileTree(it) {
                exclude("com/revolut/shaded/org/testcontainers/**/*")
            }
        })
    }
}


dependencies {
    implementation("org.jooq:jooq-codegen:3.14.15")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")
    implementation("com.github.docker-java:docker-java-transport-okhttp:3.2.14")
    implementation("org.flywaydb:flyway-core:9.14.1")
    implementation("org.zeroturnaround:zt-exec:1.12")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    testCompileOnly(gradleTestKit())
}
