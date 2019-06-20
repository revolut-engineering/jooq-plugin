package com.revolut.jooq

import groovy.lang.Closure
import groovy.lang.DelegatingMetaClass
import groovy.lang.GroovyObject
import org.codehaus.groovy.runtime.wrappers.PojoWrapper
import org.jooq.util.jaxb.Generator

class ClosureGeneratorCustomizer(closure: Closure<Generator>) : GeneratorCustomizer {
    private val closure = closure.dehydrate();

    override fun execute(generator: Generator) {
        val groovyWrappedGenerator = generator.toGroovyObject()
        closure.rehydrate(groovyWrappedGenerator, groovyWrappedGenerator, groovyWrappedGenerator).call(groovyWrappedGenerator)
    }

    private fun Generator.toGroovyObject(): GroovyObject {
        val groovyObject = PojoWrapper(this, this::class.java)
        groovyObject.metaClass = DelegatingMetaClass(this::class.java)
        return groovyObject
    }
}