package com.wolfyscript.customcrafting.core.configuration

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.cli.CLISettings
import com.wolfyscript.customcrafting.core.configuration.gui.GUISettings
import com.wolfyscript.customcrafting.core.configuration.mechanics.GameMechanicSettings
import com.wolfyscript.customcrafting.core.configuration.resources.BackupSettings
import com.wolfyscript.customcrafting.core.configuration.resources.BackupSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.DirectoryBackupDestinationSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.DirectorySourceSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.FilterEntryImpl
import com.wolfyscript.customcrafting.core.configuration.resources.FilterSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.recipes.RecipeBookSettings
import com.wolfyscript.customcrafting.core.configuration.recipes.RecipeBookSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.recipes.WorkstationSettings
import com.wolfyscript.customcrafting.core.configuration.recipes.WorkstationSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.ResourceSettings
import com.wolfyscript.customcrafting.core.configuration.resources.ResourceSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.SQLSourceSettingsImpl
import com.wolfyscript.customcrafting.core.configuration.resources.SourceSettings
import com.wolfyscript.customcrafting.core.util.exportResource
import com.wolfyscript.jackson.dataformat.hocon.HoconMapper
import java.io.File

class ConfigurationManagerImpl(val customCrafting: CustomCrafting, val rootDir: File) : ConfigurationManager {

    private val configDir = File(rootDir, "config")
    private val configMapper = HoconMapper()

    override var resourceSettings: ResourceSettings = ResourceSettingsImpl(emptyList(), BackupSettingsImpl(emptyList()))
    override var recipeBookSettings: RecipeBookSettings = RecipeBookSettingsImpl()
    override var workstationSettings: WorkstationSettings = WorkstationSettingsImpl()
    override val gameMechanicSettings: GameMechanicSettings
        get() = TODO("Not yet implemented")
    override val guiSettings: GUISettings
        get() = TODO("Not yet implemented")
    override val cliSettings: CLISettings
        get() = TODO("Not yet implemented")

    private val resourcesSettingsFile = File(configDir, "resources/resources.conf")
    private val recipeBookSettingsFile = File(configDir, "recipes/recipe_book.conf")
    private val workstationSettingsFile = File(configDir, "recipes/workstations.conf")
    // The mechanics/gui/cli settings files were declared here but never read by anything — their
    // accessors above are still `TODO()`. Declare them again next to the code that loads them.

    init {
        val mappingModule = SimpleModule().apply {
            addAbstractTypeMapping(
                ResourceSettings::class.java,
                ResourceSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                RecipeBookSettings::class.java,
                RecipeBookSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                WorkstationSettings::class.java,
                WorkstationSettingsImpl::class.java
            )

            // Source Settings
            addAbstractTypeMapping(
                SourceSettings.SQLSourceSettings::class.java,
                SQLSourceSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                SourceSettings.DirectorySourceSettings::class.java,
                DirectorySourceSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                SourceSettings.FilterSettings::class.java,
                FilterSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                SourceSettings.FilterSettings.FilterEntry::class.java,
                FilterEntryImpl::class.java
            )

            // Backup Settings
            addAbstractTypeMapping(
                BackupSettings::class.java,
                BackupSettingsImpl::class.java
            )
            addAbstractTypeMapping(
                BackupSettings.DirectoryBackupDestinationSettings::class.java,
                DirectoryBackupDestinationSettingsImpl::class.java
            )

        }
        configMapper.registerModule(mappingModule)
        configMapper.registerKotlinModule()
    }

    fun saveDefaults() {
        if (!resourcesSettingsFile.exists()) {
            exportResource(
                "com/wolfyscript/customcrafting/configuration/default/resources/resources.conf",
                resourcesSettingsFile
            )
        }
        if (!recipeBookSettingsFile.exists()) {
            exportResource(
                "com/wolfyscript/customcrafting/configuration/default/recipes/recipe_book.conf",
                recipeBookSettingsFile
            )
        }
        if (!workstationSettingsFile.exists()) {
            exportResource(
                "com/wolfyscript/customcrafting/configuration/default/recipes/workstations.conf",
                workstationSettingsFile
            )
        }
    }

    /**
     * Reads the configuration from disk.
     *
     * The parsing used to happen in the constructor while this method only logged a line, so
     * `load()` (and therefore a reload) did nothing, and a malformed config threw straight out of
     * the constructor instead of falling back to the defaults.
     */
    override fun load() {
        customCrafting.logger.info("Loading configurations...")
        saveDefaults()

        resourceSettings = try {
            configMapper.readValue<ResourceSettings>(resourcesSettingsFile)
        } catch (ex: Exception) {
            customCrafting.logger.error(
                "Failed to parse $resourcesSettingsFile; falling back to the built-in defaults", ex
            )
            ResourceSettingsImpl(emptyList(), BackupSettingsImpl(emptyList()))
        }

        recipeBookSettings = try {
            configMapper.readValue<RecipeBookSettings>(recipeBookSettingsFile)
        } catch (ex: Exception) {
            customCrafting.logger.error(
                "Failed to parse $recipeBookSettingsFile; falling back to the built-in defaults", ex
            )
            RecipeBookSettingsImpl()
        }
        workstationSettings = try {
            configMapper.readValue<WorkstationSettings>(workstationSettingsFile)
        } catch (ex: Exception) {
            customCrafting.logger.error(
                "Failed to parse $workstationSettingsFile; falling back to the built-in defaults", ex
            )
            WorkstationSettingsImpl()
        }

        customCrafting.logger.info("Recipe book: $recipeBookSettings")
        customCrafting.logger.info("Workstations: $workstationSettings")
    }

}