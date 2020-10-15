/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.impl

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.test.TestDisposable
import org.jetbrains.kotlin.test.components.*
import org.jetbrains.kotlin.test.model.*

class TestConfiguration(
    defaultsProvider: DefaultsProvider,
    assertions: Assertions,

    frontendFacades: List<Constructor<FrontendFacade<*, *>>>,
    frontend2BackendConverters: List<Constructor<Frontend2BackendConverter<*, *>>>,
    backendFacades: List<Constructor<BackendFacade<*, *>>>,

    frontendHandlers: List<Constructor<FrontendResultsHandler<*>>>,
    backendHandlers: List<Constructor<BackendInitialInfoHandler<*>>>,
    artifactsHandlers: List<Constructor<ArtifactsResultsHandler<*>>>,

    sourcePreprocessors: List<SourceFilePreprocessor>,
) {
    val rootDisposable: Disposable = TestDisposable()

    val configurationComponents: ConfigurationComponents = run {
        val sourceFileProvider = SourceFileProviderImpl(sourcePreprocessors)
        val kotlinCoreEnvironmentProvider = KotlinCoreEnvironmentProviderImpl(sourceFileProvider, rootDisposable)
        ConfigurationComponents(kotlinCoreEnvironmentProvider, sourceFileProvider, assertions, defaultsProvider)
    }

    val frontendFacades: Map<FrontendKind, FrontendFacade<*, *>> =
        frontendFacades
            .map { it.invoke(configurationComponents) }
            .groupBy { it.frontendKind }
            .mapValues { it.value.singleOrNull() ?: manyFacadesError("frontend facades", "source -> ${it.key}") }

    val frontend2BackendConverters: Map<FrontendKind, Map<BackendKind, Frontend2BackendConverter<*, *>>> =
        frontend2BackendConverters
            .map { it.invoke(configurationComponents) }
            .groupBy { it.frontendKind }
            .mapValues { (frontendKind, converters) ->
                converters.groupBy { it.backendKind }.mapValues {
                    it.value.singleOrNull() ?: manyFacadesError("converters", "$frontendKind -> ${it.key}")
                }
            }

    val backendFacades: Map<BackendKind, Map<ArtifactKind, BackendFacade<*, *>>> = backendFacades
        .map { it.invoke(configurationComponents) }
        .groupBy { it.backendKind }
        .mapValues { (backendKind, facades) ->
            facades.groupBy { it.artifactKind }.mapValues {
                it.value.singleOrNull() ?: manyFacadesError("backend facades", "$backendKind -> ${it.key}")
            }
        }

    val frontendHandlers: Map<FrontendKind, List<FrontendResultsHandler<*>>> =
        frontendHandlers.map { it.invoke(configurationComponents) }.groupBy { it.frontendKind }

    val backendHandlers: Map<BackendKind, List<BackendInitialInfoHandler<*>>> =
        backendHandlers.map { it.invoke(configurationComponents) }.groupBy { it.backendKind }

    val artifactsHandlers: Map<ArtifactKind, List<ArtifactsResultsHandler<*>>> =
        artifactsHandlers.map { it.invoke(configurationComponents) }.groupBy { it.artifactKind }

    private fun manyFacadesError(name: String, kinds: String): Nothing {
        error("Too many $name passed for $kinds configuration")
    }
}

typealias Constructor<T> = (ConfigurationComponents) -> T

class TestConfigurationBuilder {
    lateinit var defaultsProvider: DefaultsProvider
    lateinit var assertions: Assertions

    private val frontendFacades: MutableList<Constructor<FrontendFacade<*, *>>> = mutableListOf()
    private val frontend2BackendConverters: MutableList<Constructor<Frontend2BackendConverter<*, *>>> = mutableListOf()
    private val backendFacades: MutableList<Constructor<BackendFacade<*, *>>> = mutableListOf()

    private val frontendHandlers: MutableList<Constructor<FrontendResultsHandler<*>>> = mutableListOf()
    private val backendHandlers: MutableList<Constructor<BackendInitialInfoHandler<*>>> = mutableListOf()
    private val artifactsHandlers: MutableList<Constructor<ArtifactsResultsHandler<*>>> = mutableListOf()

    private val sourcePreprocessors: MutableList<SourceFilePreprocessor> = mutableListOf()

    inline fun globalDefaults(init: DefaultsProviderBuilder.() -> Unit) {
        defaultsProvider = DefaultsProviderBuilder().apply(init).build()
    }

    fun useFrontendFacades(vararg constructor: Constructor<FrontendFacade<*, *>>) {
        frontendFacades += constructor
    }

    fun useBackendFacades(vararg constructor: Constructor<BackendFacade<*, *>>) {
        backendFacades += constructor
    }

    fun useFrontend2BackendConverters(vararg constructor: Constructor<Frontend2BackendConverter<*, *>>) {
        frontend2BackendConverters += constructor
    }

    fun useFrontendHandlers(vararg constructor: Constructor<FrontendResultsHandler<*>>) {
        frontendHandlers += constructor
    }

    fun useBackendHandlers(vararg constructor: Constructor<BackendInitialInfoHandler<*>>) {
        backendHandlers += constructor
    }

    fun useArtifactsHandlers(vararg constructor: Constructor<ArtifactsResultsHandler<*>>) {
        artifactsHandlers += constructor
    }

    fun useSourcePreprocessor(vararg preprocessors: SourceFilePreprocessor) {
        sourcePreprocessors += preprocessors
    }

    fun build(): TestConfiguration {
        return TestConfiguration(
            defaultsProvider,
            assertions,
            frontendFacades,
            frontend2BackendConverters,
            backendFacades,
            frontendHandlers,
            backendHandlers,
            artifactsHandlers,
            sourcePreprocessors
        )
    }
}

inline fun testConfiguration(init: TestConfigurationBuilder.() -> Unit): TestConfiguration {
    return TestConfigurationBuilder().apply(init).build()
}
