package com.wolfyscript.customcrafting.fabric

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.recipe.RecipeManager
import com.wolfyscript.customcrafting.fabric.inject.RecipeManagerCustomRecipesExt
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientManager
import com.wolfyscript.customcrafting.core.resource.ResourceManager
import com.wolfyscript.customcrafting.core.server.CustomCraftingServer
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.identifier.Key
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import java.io.File

class CustomCraftingServerFabric(val customCrafting: CustomCrafting, val minecraftServer: MinecraftServer) :
    CustomCraftingServer {

    override val resourceManager: ResourceManager = ResourceManager.createNewForDir(
        customCrafting,
        File(FabricLoader.getInstance().configDir.toFile(), Key.CUSTOMCRAFTING_NAMESPACE)
    )
    override val ingredientManager: IngredientManager = IngredientManager.createNew(customCrafting)
    override val recipeManager: RecipeManager = RecipeManager.createNew(customCrafting)

    init {
        resourceManager.resourceLoader.registerListener(recipeManager)
    }

    override fun onLoad() {
        ScafallProvider.get().dependencyManager.onAllDependenciesInitialized {
            resourceManager.loadResources()
        }

        (minecraftServer.recipeManager as RecipeManagerCustomRecipesExt).registerProxyRecipes()
    }

    /**
     * Called from the reload, off the main thread, once the new recipes are parsed and indexed.
     *
     * `registerProxyRecipes` rebuilds the server's RecipeMap and calls `finalizeRecipeLoading`, so it
     * must run on the server thread; hop there explicitly rather than doing it on the reload thread.
     */
    override fun onRecipesReloaded() {
        // NOTE: `recipe_book.conf: registerPlaceholders` is deliberately NOT honoured here. On
        // Fabric the proxy recipes are not a recipe-book convenience — they are how custom recipes
        // enter the vanilla RecipeMap in the first place, so skipping them would disable custom
        // recipes far more broadly than on Spigot/Paper. The option is documented as Spigot-only.
        if (!customCrafting.configurationManager.recipeBookSettings.registerPlaceholders) {
            customCrafting.logger.warn(
                "[Recipes] recipe_book.conf: registerPlaceholders = false is ignored on Fabric; " +
                        "the proxy recipes are required for custom recipes to work at all here."
            )
        }
        ScafallProvider.get().scheduler.sync(customCrafting) {
            (minecraftServer.recipeManager as RecipeManagerCustomRecipesExt).registerProxyRecipes()
        }
    }

    override fun onUnload() {

    }
}