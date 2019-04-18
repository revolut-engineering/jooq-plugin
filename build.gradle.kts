plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.10.1"
    groovy
    id("com.gradle.build-scan") version "2.2.1"
    jacoco
    id("pl.droidsonroids.jacoco.testkit") version "1.0.3"
    `maven-publish`
}

repositories {
    jcenter()
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

group = "com.revolut.jooq"
version = "0.0.1"

gradlePlugin {
    plugins.create("jooqDockerPlugin") {
        id = "com.revolut.jooq-docker"
        implementationClass = "com.revolut.jooq.JooqDockerPlugin"
        version = project.version
    }
}

pluginBundle {
    website = "http://www.gradle.org/"
    vcsUrl = "https://github.com/adik993/jooq-plugin"

    description = "Generates jOOQ classes using dockerized database"

    (plugins) {
        "jooqDockerPlugin" {
            displayName = "jOOQ Docker Plugin"
            tags = listOf("jooq", "docker", "db")
            version = "0.0.1"
        }
    }
}

tasks {
    withType<Test>().configureEach {
        if (JavaVersion.current().isJava9Compatible) {
            jvmArgs("--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED")
            jvmArgs("--illegal-access=deny")
        }
    }

    jacocoTestReport {
        reports {
            xml.isEnabled = false
            html.isEnabled = false
        }
        setDependsOn(withType<Test>())
    }
}


dependencies {
    implementation("org.jooq:jooq-codegen:3.11.11")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
    implementation("com.github.docker-java:docker-java:3.1.2")
    implementation("org.flywaydb:flyway-core:5.2.4")

    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    testCompileOnly(gradleTestKit())
}