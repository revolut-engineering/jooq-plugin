package com.revolut.jooq

import org.jooq.util.CatalogDefinition
import org.jooq.util.DefaultGeneratorStrategy
import org.jooq.util.Definition
import org.jooq.util.SchemaDefinition

class SchemaPackageRenameGeneratorStrategy : DefaultGeneratorStrategy() {
    companion object {
        var schemaToPackageMapping = emptyMap<String, String>()
    }

    override fun getJavaIdentifier(definition: Definition?): String {
        if (isEligibleForRename(definition)) {
            return getMappingForDefinition(definition)!!
        } else {
            return super.getJavaIdentifier(definition)
        }
    }

    private fun isEligibleForRename(definition: Definition?) =
            isSchemaOrCatalog(definition) && getMappingForDefinition(definition) != null

    private fun isSchemaOrCatalog(definition: Definition?) =
            definition is SchemaDefinition || definition is CatalogDefinition

    private fun getMappingForDefinition(definition: Definition?) =
            schemaToPackageMapping[definition?.getSchemaName()]

    private fun Definition.getSchemaName(): String {
        return inputName
    }

}