package com.revolut.jooq

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location.FILESYSTEM_PREFIX
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jooq.codegen.GenerationTool
import org.jooq.codegen.JavaGenerator
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Target
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader

open class GenerateJooqClassesTask : DefaultTask() {
    private lateinit var jdbcAwareClassLoader: ClassLoader
    private lateinit var extension: JooqExtension
    private lateinit var generatorConfig: Generator

    @Input
    var schemas = arrayOf("public")
    @Input
    var basePackageName = "org.jooq.generated"
    @InputFiles
    val inputDirectory = project.objects.fileCollection().from("src/main/resources/db/migration")
    @OutputDirectory
    val outputDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated-jooq"))

    init {
        group = "jooq"
        project.afterEvaluate {
            extension = project.extensions.getByName("jooq") as JooqExtension
            generatorConfig = Generator()
                    .withName(JavaGenerator::class.qualifiedName)
                    .withDatabase(Database()
                            .withName(extension.jdbc.jooqMetaName)
                            .withSchemata(schemas.map { Schema().withInputSchema(it) })
                            .withSchemaVersionProvider(FlywaySchemaVersionProvider::class.qualifiedName)
                            .withIncludes(".*")
                            .withExcludes(""))
                    .withTarget(Target()
                            .withPackageName(basePackageName)
                            .withDirectory(outputDirectory.asFile.get().toString())
                            .withClean(true))
            val sourceSets = project.properties["sourceSets"] as SourceSetContainer
            sourceSets.getByName("main").java.srcDir(outputDirectory.get())
            jdbcAwareClassLoader = buildJdbcArtifactsAwareClassLoader()
        }
    }

    @Suppress("unused")
    fun customizeGenerator(customizer: Action<Generator>) {
        doFirst {
            customizer.execute(generatorConfig)
        }
    }

    @TaskAction
    fun generateClasses() {
        val docker = Docker(
                extension.image.getImageName(),
                extension.image.envVars,
                extension.db.port to extension.image.exposedPort,
                extension.image.getRedinessCommand(),
                extension.image.containerName)
        docker.use {
            it.runInContainer {
                migrateDb()
                generateJooqClasses()
            }
        }
    }

    private fun migrateDb() {
        Flyway.configure(jdbcAwareClassLoader)
                .dataSource(extension.db.getUrl(), extension.db.username, extension.db.password)
                .schemas(*schemas)
                .locations(*inputDirectory.map { "$FILESYSTEM_PREFIX${it.absolutePath}" }.toTypedArray())
                .load()
                .migrate()
    }

    private fun generateJooqClasses() {
        FlywaySchemaVersionProvider.primarySchema = schemas.first()
        val tool = GenerationTool()
        tool.setClassLoader(jdbcAwareClassLoader)
        tool.run(Configuration()
                .withLogging(Logging.DEBUG)
                .withJdbc(Jdbc()
                        .withDriver(extension.jdbc.driverClassName)
                        .withUrl(extension.db.getUrl())
                        .withUser(extension.db.username)
                        .withPassword(extension.db.password))
                .withGenerator(generatorConfig))
    }

    private fun buildJdbcArtifactsAwareClassLoader(): ClassLoader {
        return URLClassLoader(resolveJdbcArtifacts(), project.buildscript.classLoader)
    }

    @Throws(IOException::class)
    private fun resolveJdbcArtifacts(): Array<URL> {
        return project.configurations.getByName("jdbc").resolvedConfiguration.resolvedArtifacts.map {
            it.file.toURI().toURL()
        }.toTypedArray()
    }
}