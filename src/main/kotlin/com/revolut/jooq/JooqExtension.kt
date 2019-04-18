package com.revolut.jooq

import org.gradle.api.Action
import java.net.ServerSocket

open class JooqExtension {
    companion object {
        const val HOST = "127.0.0.1"
    }

    val db = Database()
    val image = Image()
    val jdbc = Jdbc()

    fun db(configure: Action<Database>) {
        configure.execute(db)
    }

    fun image(configure: Action<Image>) {
        configure.execute(image)
    }

    fun jdbc(configure: Action<Jdbc>) {
        configure.execute(jdbc)
    }


    inner class Database {
        var username = "postgres"
        var password = "postgres"
        var name = "postgres"
        var port = 5432

        internal fun getUrl(): String {
            return "${jdbc.schema}://$HOST:${image.exposedPort}/$name${jdbc.urlQueryParams}"
        }
    }

    inner class Image {
        var repository = "postgres"
        var tag = "11.2-alpine"
        var envVars: Map<String, Any> = mapOf("POSTGRES_USER" to db.username, "POSTGRES_PASSWORD" to db.password, "POSTGRES_DB" to db.name)
        var containerName = "uniqueContainerName"
        var exposedPort = lookupFreePort()
        var readinessProbe = { host: String, port: Int ->
            arrayOf("sh", "-c", "until pg_isready -h $host -p $port; do echo waiting for db; sleep 1; done;")
        }

        internal fun getImageName(): String {
            return "$repository:$tag"
        }

        internal fun getRedinessCommand(): Array<String> {
            return readinessProbe(HOST, db.port)
        }

        private fun lookupFreePort(): Int {
            ServerSocket(0).use {
                return it.localPort
            }
        }
    }

    inner class Jdbc {
        var schema = "jdbc:postgresql"
        var driverClassName = "org.postgresql.Driver"
        var jooqMetaName = "org.jooq.meta.postgres.PostgresDatabase"
        var urlQueryParams = ""
    }
}

