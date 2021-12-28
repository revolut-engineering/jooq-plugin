package com.revolut.jooq

import ChildFirstClassLoader
import groovy.lang.Closure
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location.FILESYSTEM_PREFIX
import org.flywaydb.core.internal.configuration.ConfigUtils.DEFAULT_SCHEMA
import org.flywaydb.core.internal.configuration.ConfigUtils.TABLE
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Target
import java.io.File
import java.io.IOException
import java.net.URL
import javax.xml.bind.JAXBContext

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

    @Internal
    var generatorConfig = project.provider(this::prepareGeneratorConfig)
        private set

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
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

    @Input
    fun getCleanedGeneratorConfig() = generatorConfig.get().apply {
        target.withDirectory("ignored")
    }

    init {
        project.plugins.withType(JavaPlugin::class.java) {
            project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.named(MAIN_SOURCE_SET_NAME) {
                java {
                    srcDir(outputDirectory)
                }
            }
        }
    }

    private fun getExtension() = project.extensions.getByName("jooq") as JooqExtension


    @Suppress("unused")
    fun customizeGenerator(customizer: Action<Generator>) {
        generatorConfig = generatorConfig.map {
            customizer.execute(it)
            it
        }
    }

    @Suppress("unused")
    fun customizeGenerator(closure: Closure<Generator>) {
        generatorConfig = generatorConfig.map {
            closure.rehydrate(it, it, it).call(it)
            it
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
                .defaultSchema(defaultFlywaySchema())
                .table(flywayTableName())
                .configuration(flywayProperties)
                .load()
                .migrate()
    }

    private fun defaultFlywaySchema() = flywayProperties[DEFAULT_SCHEMA] ?: schemas.first()

    private fun flywayTableName() = flywayProperties[TABLE] ?: "flyway_schema_history"

    private fun generateJooqClasses(jdbcAwareClassLoader: ClassLoader, dbHost: String) {
        project.delete(outputDirectory)
        val config = prepareConfig(dbHost)
        val configFile = writeConfigToTemporaryFile(config)
        generateJooqClassesUsingClassloader(jdbcAwareClassLoader, configFile)
    }

    private fun prepareConfig(dbHost: String): Configuration {
        val db = getDb()
        val jdbc = getJdbc()
        val generator = generatorConfig.get()
        excludeFlywaySchemaIfNeeded(generator)
        return Configuration()
                .withLogging(Logging.DEBUG)
                .withJdbc(Jdbc()
                        .withDriver(jdbc.driverClassName)
                        .withUrl(db.getUrl(dbHost))
                        .withUser(db.username)
                        .withPassword(db.password))
                .withGenerator(generator)
    }

    private fun writeConfigToTemporaryFile(config: Configuration): File {
        val configFile = File(temporaryDir, "config.xml")
        configFile.writer().use {
            JAXBContext.newInstance(config.javaClass)
                    .createMarshaller()
                    .marshal(config, it)
        }
        if (logger.isDebugEnabled) {
            logger.debug("config for generator: {}", configFile.readText())
        }
        return configFile
    }

    private fun generateJooqClassesUsingClassloader(jdbcAwareClassLoader: ClassLoader, configFile: File) {
        jdbcAwareClassLoader.run {
            loadClass(FlywaySchemaVersionProvider::class.qualifiedName)
                    .getDeclaredMethod("setup", String::class.java, String::class.java)
                    .invoke(null, defaultFlywaySchema(), flywayTableName())
            loadClass(SchemaPackageRenameGeneratorStrategy::class.qualifiedName)
                    .getDeclaredMethod("setup", Map::class.java)
                    .invoke(null, schemaToPackageMapping.toMap())
            loadClass("org.jooq.codegen.GenerationTool")
                    .getDeclaredMethod("main", Array<String>::class.java)
                    .invoke(null, arrayOf(configFile.absolutePath))
        }
    }

    private fun prepareGeneratorConfig(): Generator {
        return Generator()
                .withName("org.jooq.codegen.JavaGenerator")
                .withStrategy(Strategy()
                        .withName(SchemaPackageRenameGeneratorStrategy::class.qualifiedName))
                .withDatabase(Database()
                        .withName(getJdbc().jooqMetaName)
                        .withSchemata(schemas.map(this::toSchemaMappingType))
                        .withSchemaVersionProvider(FlywaySchemaVersionProvider::class.qualifiedName)
                        .withIncludes(".*")
                        .withExcludes(""))
                .withTarget(Target()
                        .withPackageName(basePackageName)
                        .withDirectory(outputDirectory.asFile.get().toString())
                        .withClean(true))
                .withGenerate(Generate())
    }

    private fun toSchemaMappingType(schemaName: String): SchemaMappingType {
        return SchemaMappingType()
                .withInputSchema(schemaName)
                .withOutputSchemaToDefault(outputSchemaToDefault.contains(schemaName))
    }

    private fun excludeFlywaySchemaIfNeeded(generator: Generator) {
        if (excludeFlywayTable)
            generator.database.withExcludes(addFlywaySchemaHistoryToExcludes(generator.database.excludes))
    }

    private fun addFlywaySchemaHistoryToExcludes(currentExcludes: String?): String {
        return listOf(currentExcludes, flywayTableName())
                .filterNot(String?::isNullOrEmpty)
                .joinToString("|")
    }

    private fun buildJdbcArtifactsAwareClassLoader(): ClassLoader {
        return ChildFirstClassLoader(
                resolveJdbcArtifacts()
                        + arrayOf(SchemaPackageRenameGeneratorStrategy.javaClass.protectionDomain.codeSource.location),
                project.buildscript.classLoader)
    }

    @Throws(IOException::class)
    private fun resolveJdbcArtifacts(): Array<URL> {
        return project.configurations.getByName("jdbc").resolvedConfiguration.resolvedArtifacts.map {
            it.file.toURI().toURL()
        }.toTypedArray()
    }
}