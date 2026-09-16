package com.wolfyscript.customcrafting.spigotlike

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.commands.CCCommands
import com.wolfyscript.customcrafting.core.recipe.RecipeManager
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientManager
import com.wolfyscript.customcrafting.core.resource.ResourceManager
import com.wolfyscript.customcrafting.core.configuration.recipes.WorkstationSettingsImpl
import com.wolfyscript.customcrafting.core.server.CustomCraftingServer
import com.wolfyscript.customcrafting.spigotlike.recipes.registerCommonRecipeListeners
import com.wolfyscript.customcrafting.spigotlike.recipes.buildDisplayRecipes
import com.wolfyscript.customcrafting.spigotlike.recipes.buildPlaceholderRecipes
import com.wolfyscript.customcrafting.spigotlike.recipes.registerBukkitRecipes
import com.wolfyscript.customcrafting.spigotlike.recipes.rememberRegisteredKeys
import com.wolfyscript.customcrafting.spigotlike.recipes.syncPlatformRecipes
import com.wolfyscript.scafall.ScafallProvider
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

class CustomCraftingServerSpigotLike(val customCrafting: CustomCrafting, val plugin: Plugin) : CustomCraftingServer {

    override val resourceManager: ResourceManager = ResourceManager.createNewForDir(customCrafting, plugin.dataFolder)
    override val ingredientManager: IngredientManager = IngredientManager.createNew(customCrafting)
    override val recipeManager: RecipeManager = RecipeManager.createNew(customCrafting)

    init {
        resourceManager.resourceLoader.registerListener(recipeManager)
    }

    override fun onLoad() {
        ScafallProvider.get().dependencyManager.onAllDependenciesInitialized {
            resourceManager.loadResources()
            val recipeBook = customCrafting.configurationManager.recipeBookSettings
            if (recipeBook.registerPlaceholders) {
                registerBukkitRecipes(buildPlaceholderRecipes(customCrafting, recipeManager.recipes()))
                registerBukkitRecipes(buildDisplayRecipes(recipeManager.recipes()))
                // One resync for the whole batch, and none at all with the recipe book switched off.
                if (recipeBook.syncToPlayers) {
                    Bukkit.updateRecipes()
                }
                // Remember what we just put into Bukkit, so the first reload knows what to remove.
                rememberRegisteredKeys(customCrafting, recipeManager.recipes())
            } else {
                customCrafting.logger.info(
                    "[Recipes] Platform recipe registration is disabled (recipe_book.conf: " +
                            "registerPlaceholders = false); custom cooking and stonecutting recipes will not work."
                )
            }
        }

        ScafallProvider.get().server?.minecraftServer?.commands?.dispatcher?.let {
            CCCommands.registerCommands(it)
        }

        val disabledWorkstations = (customCrafting.configurationManager.workstationSettings as? WorkstationSettingsImpl)
            ?.disabledNames() ?: emptyList()
        if (disabledWorkstations.isNotEmpty()) {
            customCrafting.logger.info(
                "[Recipes] Disabled workstations (workstations.conf): ${disabledWorkstations.joinToString(", ")}"
            )
        }
        Bukkit.getPluginManager().registerCommonRecipeListeners(plugin, customCrafting)
    }

    /**
     * Called from the reload, off the main thread, once the new recipes are parsed and indexed.
     * The Bukkit registry update is batched onto the main thread by [syncPlatformRecipes].
     */
    override fun onRecipesReloaded() {
        syncPlatformRecipes(customCrafting, recipeManager.recipes())
    }

    override fun onUnload() {

    }


}