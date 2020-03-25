package com.revolut.jooq

import org.jooq.impl.DSL.*
import org.jooq.meta.SchemaDefinition
import org.jooq.meta.SchemaVersionProvider

class FlywaySchemaVersionProvider : SchemaVersionProvider {
    companion object {
        private val defaultSchemaName = ThreadLocal<String>()
        private val flywayTableName = ThreadLocal<String>()

        fun setup(defaultSchemaName: String, flywayTableName: String) {
            this.defaultSchemaName.set(defaultSchemaName)
            this.flywayTableName.set(flywayTableName)
        }
    }

    override fun version(schema: SchemaDefinition): String {
        return schema.database.create()
                .select(max(field("version")).`as`("max_version"))
                .from(table(name(defaultSchemaName.get(), flywayTableName.get())))
                .fetchSingle("max_version", String::class.java)
    }
}