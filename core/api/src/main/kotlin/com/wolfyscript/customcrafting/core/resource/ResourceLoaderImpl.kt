package com.wolfyscript.customcrafting.core.resource

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.resources.ResourceSettings
import com.wolfyscript.scafall.identifier.Key
import java.io.File

internal class ResourceLoaderImpl(
    val customCrafting: CustomCrafting,
    val settings: ResourceSettings,
    override val directory: File,
) :
    ResourceLoader {

    val listeners: MutableList<ResourceListener> = mutableListOf()

    override val sources: List<Source> = settings.sources.map {
        customCrafting.logger.info("[Resources] Construct sources: $it")
        return@map it.configureFor(customCrafting, this)
    }

    override fun registerListener(listener: ResourceListener) {
        listeners.add(listener)
    }

    override fun loadResources() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        customCrafting.logger.info("[Resources] $directory: Initiate resource loading with listeners: $listeners")
        customCrafting.logger.info("[Resources] $directory: Preparing resources...")
        for (listener in listeners) {
            listener.onPrepare(this)
        }

        customCrafting.logger.info("[Resources] $directory: Loading resources...")
        for (listener in listeners) {
            listener.onInitialLoad(this)
        }

        customCrafting.logger.info("[Resources] $directory: Finalize resources...")
        for (listener in listeners) {
            listener.onFinalize(this)
        }
    }

    override fun reloadResources() {
        customCrafting.logger.info("[Resources] $directory: Reloading resources...")
        for (listener in listeners) {
            listener.onReload(this)
        }

        customCrafting.logger.info("[Resources] $directory: Finalize resources...")
        for (listener in listeners) {
            listener.onFinalize(this)
        }
    }

    override fun <T : Any> save(type: DataType<T>, key: Key, value: T): Result<Boolean> {
        var storedAnywhere = false
        val failures = mutableListOf<Throwable>()
        for (destination in sources) {
            if (!(destination.filter?.accepts(key) ?: true)) {
                continue
            }
            val result = destination.save(type, key, value)
            // A destination that fails used to be indistinguishable from one that succeeded: the
            // Result was dropped on the floor and the caller told the user the save went through.
            val error = result.exceptionOrNull()
            if (error != null) {
                customCrafting.logger.error("[Resources] $directory: Failed to save $key to $destination", error)
                failures.add(error)
                continue
            }
            if (result.getOrNull() == true) {
                storedAnywhere = true
                if (!destination.settings.propagateSavedResources) {
                    break
                }
            }
        }
        if (storedAnywhere) {
            return Result.success(true)
        }
        val cause = failures.firstOrNull()
            ?: IllegalStateException("No configured source accepted the resource $key")
        failures.drop(1).forEach { cause.addSuppressed(it) }
        return Result.failure(cause)
    }

    override fun delete(type: DataType<Any>, key: Key) {
        for (destination in sources) {
            if (!(destination.filter?.accepts(key) ?: true)) {
                continue
            }
            val result = destination.delete(type, key)
            if (result.isSuccess && result.getOrNull() == true) {
                // TODO: Propagate deletion?
            }
        }
    }

    data class LoadedObjectImpl<T>(override val key: Key, override val value: T) : LoadedObject<T>

}
