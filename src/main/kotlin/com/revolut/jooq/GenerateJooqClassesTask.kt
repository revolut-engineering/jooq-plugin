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
    private lateinit var generatorConfig: Generator

    @Input
    var schemas = arrayOf("public")
    @Input
    var basePackageName = "org.jooq.generated"

    @InputFiles
    val inputDirectory = project.objects.fileCollection().from("src/main/resources/db/migration")
    @OutputDirectory
    val outputDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated-jooq"))

    @Input
    fun getDb() = getExtension().db

    @Input
    fun getJdbc() = getExtension().jdbc

    @Input
    fun getImage() = getExtension().image


    init {
        project.afterEvaluate {
            generatorConfig = Generator()
                    .withName(JavaGenerator::class.qualifiedName)
                    .withDatabase(Database()
                            .withName(getJdbc().jooqMetaName)
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
        }
    }

    private fun getExtension() = project.extensions.getByName("jooq") as JooqExtension


    @Suppress("unused")
    fun customizeGenerator(customizer: Action<Generator>) {
        doFirst {
            customizer.execute(generatorConfig)
        }
    }

    @TaskAction
    fun generateClasses() {
        val image = getImage()
        val db = getDb()
        val jdbcAwareClassLoader = buildJdbcArtifactsAwareClassLoader()
        val docker = Docker(
                image.getImageName(),
                image.envVars,
                db.port to db.exposedPort,
                image.getReadinessCommand(),
                image.containerName)
        docker.use {
            it.runInContainer {
                migrateDb(jdbcAwareClassLoader)
                generateJooqClasses(jdbcAwareClassLoader)
            }
        }
    }

    private fun migrateDb(jdbcAwareClassLoader: ClassLoader) {
        val db = getDb()
        Flyway.configure(jdbcAwareClassLoader)
                .dataSource(db.getUrl(), db.username, db.password)
                .schemas(*schemas)
                .locations(*inputDirectory.map { "$FILESYSTEM_PREFIX${it.absolutePath}" }.toTypedArray())
                .load()
                .migrate()
    }

    private fun generateJooqClasses(jdbcAwareClassLoader: ClassLoader) {
        val db = getDb()
        val jdbc = getJdbc()
        FlywaySchemaVersionProvider.primarySchema = schemas.first()
        val tool = GenerationTool()
        tool.setClassLoader(jdbcAwareClassLoader)
        tool.run(Configuration()
                .withLogging(Logging.DEBUG)
                .withJdbc(Jdbc()
                        .withDriver(jdbc.driverClassName)
                        .withUrl(db.getUrl())
                        .withUser(db.username)
                        .withPassword(db.password))
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