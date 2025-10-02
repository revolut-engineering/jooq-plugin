package com.revolut.jooq

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS
import org.junit.jupiter.api.io.TempDir
import java.lang.System.lineSeparator
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class FunctionalSpec {
    @field:TempDir(cleanup = ON_SUCCESS)
    lateinit var tmpDir: Path

    val buildFile by lazy { ensureFileExists("build.gradle.kts") }
    val settingsFile by lazy { ensureFileExists("settings.gradle.kts") }
    val gradleProperties by lazy { ensureFileExists("gradle.properties") }

    @BeforeEach
    fun setup() {
        settingsFile.writeText(
            """
            rootProject.name = "root"
            buildCache.local.directory = file("${tmpDir.resolve(".build-cache").absolutePathString()}")
            """.trimIndent(),
        )
        withGradleProperty("org.gradle.parallel", "true")
        withGradleProperty("org.gradle.caching", "true")
    }

    fun file(fileName: String): Path = ensureFileExists(fileName)

    fun withGradleProperty(
        property: String,
        value: Any,
    ) {
        gradleProperties appendLines "$property=$value"
    }

    fun buildDir(): Path = tmpDir.resolve("build")

    fun outputFile(fileName: String): Path = buildDir().resolve(fileName)

    fun GradleVersion.run(
        tasks: String,
        properties: Map<String, String> = emptyMap(),
        configurationCache: Boolean = false,
    ): BuildResult {
        return runner(tasks, properties, configurationCache = configurationCache)
            .build()
    }

    fun GradleVersion.runAndFail(
        tasks: String,
        properties: Map<String, String> = emptyMap(),
        configurationCache: Boolean = false,
    ): BuildResult {
        return runner(tasks, properties, configurationCache = configurationCache)
            .buildAndFail()
    }

    infix fun Path.appendLines(text: String) = appendText(text + lineSeparator())

    var Path.text: String
        get() = readText()
        set(value) = writeText(value)

    private fun GradleVersion.runner(
        tasks: String,
        properties: Map<String, String> = emptyMap(),
        configurationCache: Boolean,
    ): GradleRunner {
        val defaultArgs = emptyList<String>()

        val propertiesAsArgs =
            properties.map { (key, value) ->
                "-P$key=$value"
            }
        val togglesAsArgs =
            mapOf(
                "configuration-cache" to configurationCache,
            ).mapNotNull { (toggle, enabled) ->
                if (enabled) "--$toggle" else null
            }
        val tasksAsArgs = tasks.split(" ")

        return GradleRunner.create()
            .withGradleVersion(this.version)
            .withProjectDir(tmpDir.toFile())
            .withPluginClasspath()
            .withArguments(defaultArgs + propertiesAsArgs + togglesAsArgs + tasksAsArgs)
            .withDebug(ManagementFactory.getRuntimeMXBean().inputArguments.any { it.contains("-agentlib:jdwp") })
            .forwardOutput()
    }

    private fun ensureFileExists(fileName: String) =
        tmpDir.resolve(fileName).apply {
            if (notExists()) {
                parent.createDirectories()
                createFile()
            }
        }
}

fun BuildTask?.shouldHaveSuccessLikeOutcome() {
    shouldNotBeNull().outcome.shouldBeIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)
}

infix fun BuildTask?.shouldHaveOutcome(expectedOutcome: TaskOutcome) {
    shouldNotBeNull().outcome shouldBe expectedOutcome
}
