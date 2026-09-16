package com.wolfyscript.customcrafting.core.resource

import com.wolfyscript.scafall.identifier.Key
import java.io.File

/**
 * Loads and Saves resources from/to specified destinations.
 * The destinations can be local or remote.
 */
interface ResourceLoader {

    val directory: File

    val sources: List<Source>

    /**
     * Registers a listener that can listen to the loading process to process custom resources, and reload resources when CustomCrafting reloads.
     */
    fun registerListener(listener: ResourceListener)

    /**
     * Loads resources from the specified destinations. First startup only.
     */
    fun loadResources()

    /**
     * Reloads resources at runtime, dispatching [ResourceListener.onReload].
     *
     * Runtime reloads used to go through [loadResources], which re-runs the whole first-startup path
     * (re-exporting defaults, re-running onPrepare) and never called the dedicated onReload hook.
     */
    fun reloadResources()

    /**
     * Stores the recipe to the destinations.
     * To which destination the recipe is stored depends on the configuration.
     *
     * @return success when at least one destination stored the value, otherwise a failure carrying
     *         the first error encountered. Callers must not report a save as done without checking.
     */
    fun <T: Any> save(type: DataType<T>, key: Key, value: T): Result<Boolean>

    /**
     * Deletes the recipe from every destination.
     */
    fun delete(type: DataType<Any>, key: Key)

}