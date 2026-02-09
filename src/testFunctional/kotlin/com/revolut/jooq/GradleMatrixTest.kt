package com.revolut.jooq

import org.gradle.api.JavaVersion
import org.gradle.api.JavaVersion.VERSION_22
import org.gradle.api.JavaVersion.VERSION_24
import org.gradle.api.JavaVersion.VERSION_25
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import org.junit.platform.commons.util.AnnotationUtils
import org.junit.platform.commons.util.AnnotationUtils.isAnnotated
import java.lang.reflect.Parameter
import java.util.stream.Stream
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.streams.asStream

@Retention(RUNTIME)
annotation class GradleVersionConfiguration(
    val gradleVersion: String,
    val supportsConfigurationCache: Boolean = false,
    val maxSupportedJavaVersion: JavaVersion,
)

@Retention(RUNTIME)
@Target(FUNCTION)
annotation class GradleVersionAtLeast(val version: String)

@Retention(RUNTIME)
@Target(FUNCTION)
annotation class GradleVersionAtMost(val version: String)

@Retention(RUNTIME)
@Target(FUNCTION)
annotation class GradleVersionExactly(val version: String)

@Retention(RUNTIME)
@Target(FUNCTION)
annotation class ConfigurationCacheUnsupported

@Retention(RUNTIME)
@Target(FUNCTION)
annotation class ConfigurationCacheSupported

@Retention(RUNTIME)
@Target(FUNCTION)
@ExtendWith(GradleVersionMatrixExtension::class)
@TestTemplate
annotation class GradleMatrixTest(
    val matrix: Array<GradleVersionConfiguration> = [
        GradleVersionConfiguration(
            "8.8",
            maxSupportedJavaVersion = VERSION_22,
            supportsConfigurationCache = true,
        ),
        GradleVersionConfiguration(
            "8.14",
            maxSupportedJavaVersion = VERSION_24,
            supportsConfigurationCache = true,
        ),
        GradleVersionConfiguration(
            "9.1.0",
            maxSupportedJavaVersion = VERSION_25,
            supportsConfigurationCache = true,
        ),
        GradleVersionConfiguration(
            "9.3.1",
            maxSupportedJavaVersion = VERSION_25,
            supportsConfigurationCache = true,
        ),
    ],
)

@Retention(RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class TargetGradleVersion

@Retention(RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigurationCacheEnabled

class GradleVersionMatrixExtension : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        return isAnnotated(context.element, GradleMatrixTest::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        val matrixAnnotation =
            AnnotationUtils.findAnnotation(context.element, GradleMatrixTest::class.java)
                .orElseThrow()
        val testMethod = context.requiredTestMethod

        return matrixAnnotation.matrix.asSequence()
            .flatMap { config ->
                sequence {
                    if (JavaVersion.current() > config.maxSupportedJavaVersion) {
                        return@sequence
                    }

                    val gradleVersion = GradleVersion.version(config.gradleVersion)
                    yield(MatrixInvocationContext(context.displayName, gradleVersion))
                    if (config.supportsConfigurationCache && testMethod.parameters.any { it.parameterizedOnConfigurationCache() }) {
                        yield(MatrixInvocationContext(context.displayName, gradleVersion, configurationCacheEnabled = true))
                    }
                }
            }
            .filter { matrixContext ->
                testMethod.annotations.all { annotation ->
                    when (annotation) {
                        is GradleVersionAtLeast -> matrixContext.gradleVersion >= GradleVersion.version(annotation.version)
                        is GradleVersionExactly -> matrixContext.gradleVersion == GradleVersion.version(annotation.version)
                        is GradleVersionAtMost -> matrixContext.gradleVersion <= GradleVersion.version(annotation.version)
                        is ConfigurationCacheSupported -> matrixContext.configurationCacheEnabled
                        is ConfigurationCacheUnsupported -> !matrixContext.configurationCacheEnabled
                        else -> true
                    }
                }
            }
            .distinct()
            .asStream()
    }

    internal data class MatrixInvocationContext(
        val displayName: String,
        val gradleVersion: GradleVersion,
        val configurationCacheEnabled: Boolean = false,
    ) : TestTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String {
            return "$displayName GradleVersion($gradleVersion) (configurationCache=$configurationCacheEnabled)"
        }

        override fun getAdditionalExtensions(): List<MatrixParameterResolver> {
            return listOf(MatrixParameterResolver(this))
        }

        internal class MatrixParameterResolver(private val context: MatrixInvocationContext) :
            ParameterResolver {
            override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
                return parameterContext.parameter.parameterizedOnGradleVersion() ||
                    parameterContext.parameter.parameterizedOnConfigurationCache()
            }

            override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
                parameterContext.findAnnotation(TargetGradleVersion::class.java).orElse(null)?.let {
                    return context.gradleVersion
                }
                parameterContext.findAnnotation(ConfigurationCacheEnabled::class.java).orElse(null)?.let {
                    return context.configurationCacheEnabled
                }
                throw UnsupportedOperationException()
            }
        }
    }
}

private fun Parameter.parameterizedOnConfigurationCache() = isAnnotated(this, ConfigurationCacheEnabled::class.java)

private fun Parameter.parameterizedOnGradleVersion() = isAnnotated(this, TargetGradleVersion::class.java)
