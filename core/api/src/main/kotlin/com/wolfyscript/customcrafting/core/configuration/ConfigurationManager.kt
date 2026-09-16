package com.wolfyscript.customcrafting.core.configuration

import com.wolfyscript.customcrafting.core.configuration.cli.CLISettings
import com.wolfyscript.customcrafting.core.configuration.gui.GUISettings
import com.wolfyscript.customcrafting.core.configuration.mechanics.GameMechanicSettings
import com.wolfyscript.customcrafting.core.configuration.recipes.RecipeBookSettings
import com.wolfyscript.customcrafting.core.configuration.recipes.WorkstationSettings
import com.wolfyscript.customcrafting.core.configuration.resources.ResourceSettings

/**
 * Handles the configurations of all the different plugin modules.
 */
interface ConfigurationManager {

    fun load()

    val resourceSettings: ResourceSettings

    /**
     * How much CustomCrafting exposes to the vanilla recipe system / recipe book.
     */
    val recipeBookSettings: RecipeBookSettings

    /**
     * Which workstation listeners are registered with the server.
     */
    val workstationSettings: WorkstationSettings

    val gameMechanicSettings: GameMechanicSettings

    val guiSettings: GUISettings

    val cliSettings: CLISettings

}