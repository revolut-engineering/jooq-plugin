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
        copyResource("testkit-gradle.properties", new File(projectDir, "gradle.properties"))
    }

    def "plugin is applicable"() {
        given:
        prepareBuildGradleFile(projectDir,
                """
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
        prepareBuildGradleFile(projectDir,
                """
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", new File(projectDir, "src/main/resources/db/migration/V01__init.sql"))

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

    def "generates jooq classes for PostgreSQL db with default config for multiple schemas"() {
        given:
        prepareBuildGradleFile(projectDir,
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
                          }
                      }
                      
                      dependencies {
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_multiple_schemas.sql"))

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
        Files.exists(generatedOther)
    }

    def "respects the generator customizations"() {
        given:
        prepareBuildGradleFile(projectDir,
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
                                  this.database.withExcludes("BAR")
                              }
                          }
                      }
                      
                      dependencies {
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_multiple_schemas.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_multiple_schemas.sql"))

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
        prepareBuildGradleFile(projectDir,
                """
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", new File(projectDir, "src/main/resources/db/migration/V01__init.sql"))

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
        prepareBuildGradleFile(projectDir,
                """
                      plugins {
                          id("com.revolut.jooq-docker")
                      }
                      
                      repositories {
                          jcenter()
                      }
                      
                      dependencies {
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", new File(projectDir, "src/main/resources/db/migration/V01__init.sql"))

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
        copyResource("/V02__add_bar.sql", new File(projectDir, "src/main/resources/db/migration/V02__add_bar.sql"))
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
                    "jdbc"("org.postgresql:postgresql:42.2.5")
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
                    "jdbc"("org.postgresql:postgresql:42.2.5")
                }
                """
        prepareBuildGradleFile(projectDir, initialBuildGradle)
        copyResource("/V01__init.sql", new File(projectDir, "src/main/resources/db/migration/V01__init.sql"))

        when:
        def initialResult = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        prepareBuildGradleFile(projectDir, extensionUpdatedBuildGradle)
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
                            this.database.withExcludes("BAR")
                        }
                    }
                }
                
                dependencies {
                    "jdbc"("org.postgresql:postgresql:42.2.5")
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
                            this.database.withExcludes(".*")
                        }
                    }
                }
                
                dependencies {
                    "jdbc"("org.postgresql:postgresql:42.2.5")
                }
                """
        copyResource("/V01__init_multiple_schemas.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_multiple_schemas.sql"))
        prepareBuildGradleFile(projectDir, initialBuildGradle)

        when:
        def initialRun = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("generateJooqClasses")
                .build()
        prepareBuildGradleFile(projectDir, updatedBuildGradle)
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
        prepareBuildGradleFile(projectDir,
                """
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
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init.sql", new File(projectDir, "src/main/resources/db/migration/V01__init.sql"))

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
        prepareBuildGradleFile(projectDir,
                """
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
                          "jdbc"("mysql:mysql-connector-java:8.0.15")
                      }
                      """)
        copyResource("/V01__init_mysql.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_mysql.sql"))

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
        prepareBuildGradleFile(projectDir,
                """
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
                          "jdbc"("org.postgresql:postgresql:42.2.5")
                      }
                      """)
        copyResource("/V01__init_with_placeholders.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_with_placeholders.sql"))

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
                          }
                      }
                      
                      dependencies {
                          jdbc "org.postgresql:postgresql:42.2.5"
                      }
                      """)
        copyResource("/V01__init_with_placeholders.sql", new File(projectDir, "src/main/resources/db/migration/V01__init_with_placeholders.sql"))

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

    private static void prepareBuildGradleFile(File dir, String script) {
        def buildGradleFile = new File(dir, "build.gradle.kts")
        buildGradleFile.write(script)
    }

    private void copyResource(String resource, File outputFile) {
        outputFile.parentFile.mkdirs()
        getClass().getResourceAsStream(resource).transferTo(new FileOutputStream(outputFile))
    }
}