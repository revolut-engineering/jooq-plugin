package com.revolut.jooq;

import org.gradle.api.Action;
import org.jooq.util.jaxb.Generator;

import java.io.Serializable;

public interface GeneratorCustomizer extends Action<Generator>, Serializable {
}
