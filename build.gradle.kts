import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import java.net.URI

plugins {
    groovy
    `kotlin-dsl`
    id("com.gradle.plugin-publish").version("1.2.1")
    id("com.github.ben-manes.versions").version("0.51.0")
    if (System.getenv().containsKey("TRAVIS") || !System.getenv().containsKey("CI")) {
        id("pl.droidsonroids.jacoco.testkit").version("1.0.12")
    }
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

group = "com.revolut.jooq"
version = "0.3.12-SNAPSHOT"

gradlePlugin {
    website.set("https://github.com/revolut-engineering/jooq-plugin")
    vcsUrl.set("https://github.com/revolut-engineering/jooq-plugin")

    plugins {
        register("jooqDockerPlugin") {
            id = "com.revolut.jooq-docker"
            implementationClass = "com.revolut.jooq.JooqDockerPlugin"
            description = "Generates jOOQ classes using dockerized database"
            displayName = "jOOQ Docker Plugin"
            tags = setOf("jooq", "docker", "db")
            version = project.version
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
        useJUnitPlatform()
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
            xml.required = true
            html.required = false
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
        gradleVersion = "8.6"
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
    implementation("com.github.docker-java:docker-java-transport-okhttp:3.3.6")
    implementation("org.flywaydb:flyway-core:6.4.3")
    implementation("org.zeroturnaround:zt-exec:1.12")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.spockframework:spock-core:2.3-groovy-3.0")
    testCompileOnly(gradleTestKit())
}
