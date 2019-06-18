# Gradle Docker jOOQ Plugin

[![Build Status](https://travis-ci.org/revolut-engineering/jooq-plugin.svg?branch=master)](https://travis-ci.org/revolut-engineering/jooq-plugin)
[![codecov](https://codecov.io/gh/revolut-engineering/jooq-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/revolut-engineering/jooq-plugin)
[![Gradle Plugins Release](https://img.shields.io/github/release/revolut-engineering/jooq-plugin.svg)](https://plugins.gradle.org/plugin/com.revolut.jooq-docker)

This repository contains Gradle plugin for generating jOOQ classes in dockerized databases.
Plugin registers task `generateJooqClasses` that does following steps:
 * pulls docker image
 * starts database container
 * runs migrations using Flyway
 * generates jOOQ classes

**Use `0.2.x` and later releases for jOOQ versions `3.11.x` and later. For earlier versions use `0.1.x` release**

# Examples

By default plugin is configured to work with PostgreSQL, so the following minimal config is enough:
```kotlin
plugins {
  id("com.revolut.jooq-docker")
}

repositories {
  jcenter()
}

dependencies {
  implementation("org.jooq:jooq:3.11.11")
  "jdbc"("org.postgresql:postgresql:42.2.5")
}
```
It will look for migration files in `src/main/resources/db/migration` and will output generated classes
to `build/generated-jooq` in package `org.jooq.generated`. All of that details can be configured on the task itself
as shown in examples below.

Configuring schema names and other parameters of the task:
```kotlin
plugins {
  id("com.revolut.jooq-docker")
}

repositories {
  jcenter()
}

tasks {
  generateJooqClasses {
    schemas = arrayOf("public", "other_schema")
    flywaySchema = "flyway" // schema for the flyway metadata table (not fed into jOOQ class generation)
    basePackageName = "org.jooq.generated"
    inputDirectory.setFrom(project.files("src/main/resources/db/migration"))
    outputDirectory.set(project.layout.buildDirectory.dir("generated-jooq"))
    flywayProperties = mapOf("flyway.placeholderReplacement" to "false")
    outputSchemaToDefault = setOf("public")
    schemaToPackageMapping = mapOf("public" to "fancy_name")
    customizeGenerator {
      /* "this" here is the org.jooq.meta.jaxb.Generator configure it as you please */
    }
  }
}

dependencies {
  implementation("org.jooq:jooq:3.11.11")
  "jdbc"("org.postgresql:postgresql:42.2.5")
}
```

To configure the plugin to work with another DB like MySQL following config can be applied:
```kotlin
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
          arrayOf("sh", "-c", "until mysqladmin -h$host -P$port -uroot -pmysql ping; do echo wait; sleep 1; done;")
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
  implementation("org.jooq:jooq:3.11.11")
  "jdbc"("mysql:mysql-connector-java:8.0.15")
}
```

To register custom types:
```kotlin
plugins {
  id("com.revolut.jooq-docker")
}

repositories {
  jcenter()
}

tasks {
  generateJooqClasses {
    customizeGenerator {
      database.withForcedTypes(
              ForcedType()
                      .withUserType("com.google.gson.JsonElement")
                      .withBinding("com.example.PostgresJSONGsonBinding")
                      .withTypes("JSONB")
      )
    }    
  }
}

dependencies {
  implementation("org.jooq:jooq:3.11.11")
  "jdbc"("org.postgresql:postgresql:42.2.5")
}
```

To enforce version of the plugin dependencies:
```kotlin
plugins {
  id("com.revolut.jooq-docker")
}

buildscript {
  repositories {
    jcenter()
  }

  dependencies {
    classpath("org.jooq:jooq-codegen:3.11.10") {
      isForce = true
    }
  }
}

repositories {
  jcenter()
}

dependencies {
  implementation("org.jooq:jooq:3.11.10")
  "jdbc"("org.postgresql:postgresql:42.2.5")
}
```