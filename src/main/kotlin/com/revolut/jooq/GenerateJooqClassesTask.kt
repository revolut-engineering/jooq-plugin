package com.revolut.jooq

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location.FILESYSTEM_PREFIX
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

open class GenerateJooqClassesTask : DefaultTask() {
    @Input
    var schemas = arrayOf("public")
    @Input @Optional
    var flywaySchema: String? = null
    @Input
    var basePackageName = "org.jooq.generated"
    @Input
    var flywayProperties = emptyMap<String, String>()
    @Input
    var outputSchemaToDefault = emptySet<String>()
    @Input
    var schemaToPackageMapping = emptyMap<String, String>()
    @Input
    val generatorCustomizer = project.objects.property(GeneratorCustomizer::class).convention(GeneratorCustomizer { })

    @InputFiles
    val inputDirectory = project.objects.fileCollection().from("src/main/resources/db/migration")
    @OutputDirectory
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
    fun getImageRepository() = getImage().repository

    @Input
    fun getImageTag() = getImage().tag

    @Input
    fun getImageEnvVars() = getImage().envVars

    @Input
    fun getContainerName() = getImage().containerName

    @Input
    fun getReadinessCommand() = getImage().getReadinessCommand()


    init {
        project.afterEvaluate {
            val sourceSets = project.properties["sourceSets"] as SourceSetContainer
            sourceSets.getByName("main").java.srcDir(outputDirectory.get())
        }
    }

    private fun getExtension() = project.extensions.getByName("jooq") as JooqExtension


    @Suppress("unused")
    fun customizeGenerator(customizer: GeneratorCustomizer) {
        generatorCustomizer.set(customizer)
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
                .schemas(*listOfNotNull(flywaySchema, *schemas).toTypedArray())
                .locations(*inputDirectory.map { "$FILESYSTEM_PREFIX${it.absolutePath}" }.toTypedArray())
                .configuration(flywayProperties)
                .load()
                .migrate()
    }

    private fun generateJooqClasses(jdbcAwareClassLoader: ClassLoader) {
        val db = getDb()
        val jdbc = getJdbc()
        FlywaySchemaVersionProvider.primarySchema = flywaySchema ?: schemas.first()
        val tool = GenerationTool()
        tool.setClassLoader(jdbcAwareClassLoader)
        tool.run(Configuration()
                .withLogging(Logging.DEBUG)
                .withJdbc(Jdbc()
                        .withDriver(jdbc.driverClassName)
                        .withUrl(db.getUrl())
                        .withUser(db.username)
                        .withPassword(db.password))
                .withGenerator(prepareGeneratorConfig()))
    }

    private fun prepareGeneratorConfig(): Generator {
        SchemaPackageRenameGeneratorStrategy.schemaToPackageMapping = schemaToPackageMapping.toMap()
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
        return generatorConfig
    }

    private fun toSchema(schemaName: String): Schema {
        return Schema()
                .withInputSchema(schemaName)
                .withOutputSchemaToDefault(outputSchemaToDefault.contains(schemaName))
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