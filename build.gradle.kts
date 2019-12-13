import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL

plugins {
    `kotlin-dsl`
    groovy
    jacoco
    `maven-publish`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.10.1"
    id("pl.droidsonroids.jacoco.testkit") version "1.0.5"
    id("com.github.ben-manes.versions").version("0.27.0")
}

repositories {
    jcenter()
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
version = "0.1.7"

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

    jacocoTestReport {
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
        gradleVersion = "6.0.1"
        distributionType = ALL
    }
}


dependencies {
    implementation("org.jooq:jooq-codegen:3.10.8")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
    implementation("com.github.docker-java:docker-java:3.1.5")
    implementation("org.flywaydb:flyway-core:6.1.1")
    implementation("org.testcontainers:testcontainers:1.12.4")

    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    testCompileOnly(gradleTestKit())
}