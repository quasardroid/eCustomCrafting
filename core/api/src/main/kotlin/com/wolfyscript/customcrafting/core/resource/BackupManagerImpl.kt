package com.wolfyscript.customcrafting.core.resource

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.resources.BackupSettings

internal class BackupManagerImpl(val customCrafting: CustomCrafting, val resourceManager: ResourceManager, val settings: BackupSettings) :
    BackupManager {

    val destinations = settings.destinations.map { it.configureFor(customCrafting, resourceManager.resourceLoader, settings) }

    override fun createBackup() {
        for (destination in destinations) {
            // The Result used to be discarded, so a backup that could not even create its directory
            // was reported as done. An IOException from one destination must also not stop the rest.
            runCatching { destination.backup() }
                .getOrElse { Result.failure(it) }
                .onFailure { customCrafting.logger.error("Backup destination $destination failed", it) }
        }
    }

}