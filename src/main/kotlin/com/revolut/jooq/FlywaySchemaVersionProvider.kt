package com.revolut.jooq

import org.jooq.impl.DSL.*
import org.jooq.meta.SchemaDefinition
import org.jooq.meta.SchemaVersionProvider

class FlywaySchemaVersionProvider : SchemaVersionProvider {
    companion object {
        val primarySchema = ThreadLocal<String>()
    }

    override fun version(schema: SchemaDefinition): String {
        return schema.database.create()
                .select(max(field("version")).`as`("max_version"))
                .from(table(name(primarySchema.get(), "flyway_schema_history")))
                .fetchSingle("max_version", String::class.java)
    }

}