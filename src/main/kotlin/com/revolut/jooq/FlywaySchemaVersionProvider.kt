package com.revolut.jooq

import org.jooq.impl.DSL.*
import org.jooq.util.SchemaDefinition
import org.jooq.util.SchemaVersionProvider

class FlywaySchemaVersionProvider : SchemaVersionProvider {
    companion object {
        private const val DEFAULT_FLYWAY_TABLE_NAME = "flyway_schema_history"
        val primarySchema = ThreadLocal<String>()
        private val tableName = ThreadLocal.withInitial { DEFAULT_FLYWAY_TABLE_NAME }

        fun overrideFlywaySchemaTableNameIfPresent(tableNameOverride: String?) {
            tableName.set(tableNameOverride ?: DEFAULT_FLYWAY_TABLE_NAME)
        }
    }

    override fun version(schema: SchemaDefinition): String {
        return schema.database.create()
                .select(max(field("version")).`as`("max_version"))
                .from(table(name(primarySchema.get(), tableName.get())))
                .fetchOne("max_version", String::class.java)
    }
}