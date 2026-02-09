import com.gradle.publish.PublishTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import org.gradle.kotlin.dsl.withType
import java.net.URI

plugins {
    groovy
    `jvm-test-suite`
    `kotlin-dsl`
    id("com.gradle.plugin-publish").version("2.0.0")
    if (System.getenv().containsKey("TRAVIS") || !System.getenv().containsKey("CI")) {
        id("pl.droidsonroids.jacoco.testkit").version("1.0.12")
    }
}

group = "com.revolut.jooq"
version = "0.3.13"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

publishing {
    repositories {
        val snapshotRepository by extra { project.findProperty("snapshotRepository")?.toString() ?: "" }
        maven { url = URI(if (isSnapshot) snapshotRepository else "") }
    }
}

tasks {
    withType<JacocoReport> {
        reports {
            xml.required = true
            html.required = false
        }
        setDependsOn(withType<Test>())
    }

    withType<PublishToMavenRepository>().configureEach {
        onlyIf("publishing SNAPSHOT release to the internal repository") { isSnapshot }
        setFinalizedBy(withType<PublishTask>())
    }

    withType<PublishTask>().configureEach {
        onlyIf("publishing release to the Gradle Plugin Portal") { !isSnapshot }
    }

    wrapper {
        gradleVersion = "9.3.1"
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
    implementation("com.github.docker-java:docker-java-transport-okhttp:3.6.0")
    implementation("org.flywaydb:flyway-core:6.4.3")
    implementation("org.zeroturnaround:zt-exec:1.12")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testCompileOnly(gradleTestKit())
}

testing {
    suites {
        @Suppress("UnstableApiUsage")
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform("io.kotest:kotest-bom:5.9.1"))
                implementation("io.kotest:kotest-assertions-core-jvm")
            }

            targets.configureEach {
                testTask {
                    testLogging {
                        events(PASSED, FAILED)
                        showExceptions = true
                        showStackTraces = true
                        showCauses = true
                        exceptionFormat = FULL
                    }
                }
            }
        }

        @Suppress("UnstableApiUsage")
        val test by existing(JvmTestSuite::class)

        @Suppress("UnstableApiUsage")
        val testFunctional by registering(JvmTestSuite::class) {
            dependencies {
                implementation(gradleTestKit())
            }

            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(test)
                }
            }
            targets.register("${name}OnJava25") {
                testTask.configure {
                    javaLauncher.set(
                        javaToolchains.launcherFor {
                            languageVersion.set(JavaLanguageVersion.of(25))
                            vendor.set(java.toolchain.vendor)
                        },
                    )
                }
            }
        }

        tasks.check {
            dependsOn(testFunctional.map { it.targets.named(it.name) })
        }
    }
}

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
        testSourceSets(sourceSets["test"], sourceSets["testFunctional"])
    }
}
