import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class JooqDockerPluginSpec extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir

    def setup() {
        projectDir = temporaryFolder.newFolder()
        copyResource("testkit-gradle.properties", "gradle.properties")
    }

    def "plugin is applicable"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      """)

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .build()

        then:
        result != null
    }

    def "generates jooq classes for PostgreSQL db with default config"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def generatedFlywayClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/FlywaySchemaHistory.java")
        Files.exists(generatedFooClass)
        Files.exists(generatedFlywayClass)
    }

    def "generates jooq classes for PostgreSQL db with default config for multiple schemas"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              schemas = arrayOf("public", "other")
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", "src/main/resources/db/migration/V01__init_multiple_schemas.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedPublic = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/Foo.java")
        def generatedOther = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/other/tables/Bar.java")
        def generatedFlywaySchemaClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/FlywaySchemaHistory.java")
        Files.exists(generatedPublic)
        Files.exists(generatedOther)
        Files.exists(generatedFlywaySchemaClass)
    }

    def "generates jooq classes for PostgreSQL db with default config for multiple schemas and renames package"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              schemas = arrayOf("public", "other")
                              schemaToPackageMapping = mapOf("public" to "fancy_name")
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", "src/main/resources/db/migration/V01__init_multiple_schemas.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedPublic = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/fancy_name/tables/Foo.java")
        def generatedOther = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/other/tables/Bar.java")
        Files.exists(generatedPublic)
        Files.exists(generatedOther)
    }

    def "respects the generator customizations"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              schemas = arrayOf("public", "other")
                              customizeGenerator {
                                  database.withExcludes("BAR")
                              }
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", "src/main/resources/db/migration/V01__init_multiple_schemas.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedPublic = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/Foo.java")
        def generatedOther = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/other/tables/Bar.java")
        Files.exists(generatedPublic)
        !Files.exists(generatedOther)
    }

    def "up to date check works for output dir"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def firstRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        def secondRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        Paths.get(projectDir.path, "build/generated-jooq").toFile().deleteDir()
        def runAfterDeletion = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        firstRun.task(":generateJooqClasses").outcome == SUCCESS
        secondRun.task(":generateJooqClasses").outcome == UP_TO_DATE
        runAfterDeletion.task(":generateJooqClasses").outcome == SUCCESS
    }

    def "up to date check works for input dir"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def firstRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        def secondRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        copyResource("/V02__add_bar.sql", "src/main/resources/db/migration/V02__add_bar.sql")
        def runAfterDeletion = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        firstRun.task(":generateJooqClasses").outcome == SUCCESS
        secondRun.task(":generateJooqClasses").outcome == UP_TO_DATE
        runAfterDeletion.task(":generateJooqClasses").outcome == SUCCESS
    }

    def "up to date check works for extension changes"() {
        given:
        def initialBuildGradle =
                """
                plugins {
                    id("com.revolut.jooq-docker")
                }
                
                jooq {
                    image {
                        tag = "11.2-alpine"
                    }
                }
                
                repositories {
                    jcenter()
                }
                
                dependencies {
                    jdbc("org.postgresql:postgresql:42.2.5")
                }
                """
        def extensionUpdatedBuildGradle =
                """
                plugins {
                    id("com.revolut.jooq-docker")
                }
                
                jooq {
                    image {
                        tag = "11.3-alpine"
                    }
                }
                
                repositories {
                    jcenter()
                }
                
                dependencies {
                    jdbc("org.postgresql:postgresql:42.2.5")
                }
                """
        prepareBuildGradleFile(initialBuildGradle)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def initialResult = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        prepareBuildGradleFile(extensionUpdatedBuildGradle)
        def resultAfterChangeToExtension = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        def finalRunNoChanges = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        initialResult.task(":generateJooqClasses").outcome == SUCCESS
        resultAfterChangeToExtension.task(":generateJooqClasses").outcome == SUCCESS
        finalRunNoChanges.task(":generateJooqClasses").outcome == UP_TO_DATE
    }

    def "up to date check works for generator customizations"() {
        given:
        def initialBuildGradle =
                """
                plugins {
                    id("com.revolut.jooq-docker")
                }
                
                repositories {
                    jcenter()
                }
                
                tasks {
                    generateJooqClasses {
                        schemas = arrayOf("public", "other")
                        customizeGenerator {
                            database.withExcludes("BAR")
                        }
                    }
                }
                
                dependencies {
                    jdbc("org.postgresql:postgresql:42.2.5")
                }
                """
        def updatedBuildGradle =
                """
                plugins {
                    id("com.revolut.jooq-docker")
                }
                
                repositories {
                    jcenter()
                }
                
                tasks {
                    generateJooqClasses {
                        schemas = arrayOf("public", "other")
                        customizeGenerator {
                            database.withExcludes(".*")
                        }
                    }
                }
                
                dependencies {
                    jdbc("org.postgresql:postgresql:42.2.5")
                }
                """
        copyResource("/V01__init_multiple_schemas.sql", "src/main/resources/db/migration/V01__init_multiple_schemas.sql")
        prepareBuildGradleFile(initialBuildGradle)

        when:
        def initialRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        prepareBuildGradleFile(updatedBuildGradle)
        def runAfterUpdate = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        def finalRunNoChanges = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        initialRun.task(":generateJooqClasses").outcome == SUCCESS
        runAfterUpdate.task(":generateJooqClasses").outcome == SUCCESS
        finalRunNoChanges.task(":generateJooqClasses").outcome == UP_TO_DATE
        def generatedPublic = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/Foo.java")
        def generatedOther = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/other/tables/Bar.java")
        !Files.exists(generatedPublic)
        !Files.exists(generatedOther)
    }

    def "generates jooq classes in a given package"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              basePackageName = "com.example"
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedClass = Paths.get(projectDir.getPath(), "build/generated-jooq/com/example/tables/Foo.java")
        Files.exists(generatedClass)
    }

    def "plugin is configurable"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      jooq {
                          image {
                              repository = "mysql"
                              tag = "8.0.15"
                              envVars = mapOf(
                                  "MYSQL_ROOT_PASSWORD" to "mysql",
                                  "MYSQL_DATABASE" to "mysql")
                              containerName = "uniqueMySqlContainerName"
                              readinessProbe = { host: String, port: Int ->
                                  arrayOf("sh", "-c", "until mysqladmin -h\$host -P\$port -uroot -pmysql ping; do echo wait; sleep 1; done;")
                              }
                          }
                          
                          db {
                              username = "root"
                              password = "mysql"
                              name = "mysql"
                              port = 3306
                          }
                          
                          jdbc {
                              schema = "jdbc:mysql"
                              driverClassName = "com.mysql.cj.jdbc.Driver"
                              jooqMetaName = "org.jooq.meta.mysql.MySQLDatabase"
                              urlQueryParams = "?useSSL=false"
                          }
                      }
                      
                      dependencies {
                          jdbc("mysql:mysql-connector-java:8.0.15")
                      }
                      """)
        copyResource("/V01__init_mysql.sql", "src/main/resources/db/migration/V01__init_mysql.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        Files.exists(generatedClass)
    }

    def "flyway configuration overridden with flywayProperties task input"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              flywayProperties = mapOf("flyway.placeholderReplacement" to "false")
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_with_placeholders.sql", "src/main/resources/db/migration/V01__init_with_placeholders.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        Files.exists(generatedClass)
    }

    def "plugin works in Groovy gradle file"() {
        given:
        def buildGradleFile = new File(projectDir, "build.gradle")
        buildGradleFile.write(
                """
                      plugins {
                          id "com.revolut.jooq-docker"
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              flywayProperties = ["flyway.placeholderReplacement": "false"]
                              customizeGenerator {
                                  database.withExcludes("BAR")
                              }
                          }
                      }
                      
                      dependencies {
                          jdbc "org.postgresql:postgresql:42.2.5"
                      }
                      """)
        copyResource("/V01__init_with_placeholders.sql", "src/main/resources/db/migration/V01__init_with_placeholders.sql")
        copyResource("/V02__add_bar.sql", "src/main/resources/db/migration/V02__add_bar.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses", "--stacktrace")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedFoo = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def generatedBar = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Bar.java")
        Files.exists(generatedFoo)
        !Files.exists(generatedBar)
    }

    def "output schema to default properly passed to jOOQ generator"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              outputSchemaToDefault = setOf("public")
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedTableClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def generatedSchemaClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/DefaultSchema.java")
        Files.exists(generatedTableClass)
        Files.exists(generatedSchemaClass)
    }

    def "exclude flyway schema history"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              excludeFlywayTable = true
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def generatedFlywayClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/FlywaySchemaHistory.java")
        Files.exists(generatedFooClass)
        Files.notExists(generatedFlywayClass)
    }

    def "exclude flyway schema history without overriding existing excludes"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              excludeFlywayTable = true
                              schemas = arrayOf("public", "other")
                              customizeGenerator {
                                  database.withExcludes("BAR")
                              }
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", "src/main/resources/db/migration/V01__init_multiple_schemas.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/Foo.java")
        def generatedBarClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/other/tables/Bar.java")
        def generatedFlywaySchemaClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/public_/tables/FlywaySchemaHistory.java")
        Files.exists(generatedFooClass)
        Files.notExists(generatedBarClass)
        Files.notExists(generatedFlywaySchemaClass)
    }

    def "outputDirectory task property is respected"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      tasks {
                          generateJooqClasses {
                              outputDirectory.set(project.layout.buildDirectory.dir("gen"))
                          }
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/gen/org/jooq/generated/tables/Foo.java")
        def generatedFlywayClass = Paths.get(projectDir.getPath(), "build/gen/org/jooq/generated/tables/FlywaySchemaHistory.java")
        Files.exists(generatedFooClass)
        Files.exists(generatedFlywayClass)
    }

    def "source sets and tasks are configured for java project"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          java
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          jdbc("org.postgresql:postgresql:42.2.5")
                          implementation("org.jooq:jooq:3.11.12")
                          implementation("javax.annotation:javax.annotation-api:1.3.2")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")
        writeProjectFile("src/main/java/com/test/Main.java",
                """
                package com.test;
                
                import static org.jooq.generated.Tables.FOO;
                
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(FOO.ID.getName());
                    }
                }
                """);

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("classes")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        result.task(":classes").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def mainClass = Paths.get(projectDir.getPath(), "build/classes/java/main/com/test/Main.class")
        Files.exists(generatedFooClass)
        Files.exists(mainClass)
    }

    def "source sets and tasks are configured for kotlin project"() {
        given:
        prepareBuildGradleFile("""
                      plugins {
                          kotlin("jvm").version("1.3.50")
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          implementation(kotlin("stdlib"))
                          jdbc("org.postgresql:postgresql:42.2.5")
                          implementation("org.jooq:jooq:3.11.12")
                          implementation("javax.annotation:javax.annotation-api:1.3.2")
                      }
                      """)
        copyResource("/V01__init.sql", "src/main/resources/db/migration/V01__init.sql")
        writeProjectFile("src/main/kotlin/com/test/Main.kt",
                """
                package com.test
                
                import org.jooq.generated.Tables.FOO
                
                fun main() = println(FOO.ID.name)
                """);

        when:
        def result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("classes")
                .build()

        then:
        result.task(":generateJooqClasses").outcome == SUCCESS
        result.task(":classes").outcome == SUCCESS
        def generatedFooClass = Paths.get(projectDir.getPath(), "build/generated-jooq/org/jooq/generated/tables/Foo.java")
        def mainClass = Paths.get(projectDir.getPath(), "build/classes/kotlin/main/com/test/MainKt.class")
        Files.exists(generatedFooClass)
        Files.exists(mainClass)
    }

    private void prepareBuildGradleFile(String script) {
        def buildGradleFile = new File(projectDir, "build.gradle.kts")
        buildGradleFile.write(script)
    }

    private void copyResource(String resource, String relativePath) {
        def file = new File(projectDir, relativePath)
        file.parentFile.mkdirs()
        getClass().getResourceAsStream(resource).transferTo(new FileOutputStream(file))
    }

    private void writeProjectFile(String relativePath, String content) {
        def file = new File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.write(content)
    }
}