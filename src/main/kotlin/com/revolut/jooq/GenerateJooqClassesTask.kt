package com.revolut.jooq

import com.revolut.jooq.GeneratorCustomizer.NOOP
import groovy.lang.Closure
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location.FILESYSTEM_PREFIX
import org.flywaydb.core.internal.configuration.ConfigUtils.TABLE
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property
import org.jooq.codegen.GenerationTool
import org.jooq.codegen.JavaGenerator
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Target
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader

@CacheableTask
open class GenerateJooqClassesTask : DefaultTask() {
    @Input
    var schemas = arrayOf("public")
    @Input
    var basePackageName = "org.jooq.generated"
    @Input
    var flywayProperties = emptyMap<String, String>()
    @Input
    var outputSchemaToDefault = emptySet<String>()
    @Input
    var schemaToPackageMapping = emptyMap<String, String>()
    @Input
    var excludeFlywayTable = false
    @Input
    val generatorCustomizer = project.objects.property(GeneratorCustomizer::class).convention(NOOP)

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val inputDirectory = project.objects.fileCollection().from("src/main/resources/db/migration")
    @OutputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val outputDirectory = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated-jooq"))

    @Internal
    fun getDb() = getExtension().db

    @Internal
    fun getJdbc() = getExtension().jdbc

    @Internal
    fun getImage() = getExtension().image

    @Input
    fun getJdbcSchema() = getJdbc().schema

    @Input
    fun getJdbcDriverClassName() = getJdbc().driverClassName

    @Input
    fun getJooqMetaName() = getJdbc().jooqMetaName

    @Input
    fun getJdbcUrlQueryParams() = getJdbc().urlQueryParams

    @Input
    fun getDbUsername() = getDb().username

    @Input
    fun getDbPassword() = getDb().password

    @Input
    fun getDbPort() = getDb().port

    @Input
    @Optional
    fun getDbHostOverride() = getDb().hostOverride

    @Input
    fun getImageRepository() = getImage().repository

    @Input
    fun getImageTag() = getImage().tag

    @Input
    fun getImageEnvVars() = getImage().envVars

    @Input
    fun getContainerName() = getImage().containerName

    @Input
    fun getReadinessProbeHost() = getImage().readinessProbeHost

    @Input
    fun getReadinessCommand() = getImage().getReadinessCommand()


    init {
        val sourceSets = project.properties["sourceSets"] as SourceSetContainer?
        sourceSets?.named("main") {
            java {
                srcDir(outputDirectory)
            }
        }
    }

    private fun getExtension() = project.extensions.getByName("jooq") as JooqExtension


    @Suppress("unused")
    fun customizeGenerator(customizer: GeneratorCustomizer) {
        generatorCustomizer.set(customizer)
    }

    @Suppress("unused")
    fun customizeGenerator(closure: Closure<Generator>) {
        generatorCustomizer.set(ClosureGeneratorCustomizer(closure))
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
                DatabaseHostResolver(db.hostOverride),
                image.containerName)
        docker.use {
            it.runInContainer {
                migrateDb(jdbcAwareClassLoader, this)
                generateJooqClasses(jdbcAwareClassLoader, this)
            }
        }
    }

    private fun migrateDb(jdbcAwareClassLoader: ClassLoader, dbHost: String) {
        val db = getDb()
        Flyway.configure(jdbcAwareClassLoader)
                .dataSource(db.getUrl(dbHost), db.username, db.password)
                .schemas(*schemas)
                .locations(*inputDirectory.map { "$FILESYSTEM_PREFIX${it.absolutePath}" }.toTypedArray())
                .configuration(flywayProperties)
                .load()
                .migrate()
    }

    private fun generateJooqClasses(jdbcAwareClassLoader: ClassLoader, dbHost: String) {
        val db = getDb()
        val jdbc = getJdbc()
        FlywaySchemaVersionProvider.primarySchema.set(schemas.first())
        FlywaySchemaVersionProvider.overrideFlywaySchemaTableNameIfPresent(flywayProperties[TABLE])
        val tool = GenerationTool()
        tool.setClassLoader(jdbcAwareClassLoader)
        tool.run(Configuration()
                .withLogging(Logging.DEBUG)
                .withJdbc(Jdbc()
                        .withDriver(jdbc.driverClassName)
                        .withUrl(db.getUrl(dbHost))
                        .withUser(db.username)
                        .withPassword(db.password))
                .withGenerator(prepareGeneratorConfig()))
    }

    private fun prepareGeneratorConfig(): Generator {
        SchemaPackageRenameGeneratorStrategy.schemaToPackageMapping.set(schemaToPackageMapping.toMap())
        val generatorConfig = Generator()
                .withName(JavaGenerator::class.qualifiedName)
                .withStrategy(Strategy()
                        .withName(SchemaPackageRenameGeneratorStrategy::class.qualifiedName))
                .withDatabase(Database()
                        .withName(getJdbc().jooqMetaName)
                        .withSchemata(schemas.map(this::toSchema))
                        .withSchemaVersionProvider(FlywaySchemaVersionProvider::class.qualifiedName)
                        .withIncludes(".*")
                        .withExcludes(""))
                .withTarget(Target()
                        .withPackageName(basePackageName)
                        .withDirectory(outputDirectory.asFile.get().toString())
                        .withClean(true))
        generatorCustomizer.get().execute(generatorConfig)
        excludeFlywaySchemaIfNeeded(generatorConfig)
        return generatorConfig
    }

    private fun toSchema(schemaName: String): Schema {
        return Schema()
                .withInputSchema(schemaName)
                .withOutputSchemaToDefault(outputSchemaToDefault.contains(schemaName))
    }

    private fun excludeFlywaySchemaIfNeeded(generator: Generator) {
        if (excludeFlywayTable)
            generator.database.withExcludes(addFlywaySchemaHistoryToExcludes(generator.database.excludes))
    }

    private fun addFlywaySchemaHistoryToExcludes(currentExcludes: String?): String {
        return listOf(currentExcludes, "flyway_schema_history")
                .filterNot(String?::isNullOrEmpty)
                .joinToString("|")
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