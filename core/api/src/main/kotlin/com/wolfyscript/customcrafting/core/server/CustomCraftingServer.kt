package com.wolfyscript.customcrafting.core.server

import com.wolfyscript.customcrafting.core.recipe.RecipeManager
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientManager
import com.wolfyscript.customcrafting.core.resource.ResourceManager
import com.wolfyscript.scafall.loader.module.Server

/**
 * The CustomCrafting API that is only available on the server (Integrated or Dedicated server)
 */
interface CustomCraftingServer : Server {

    val recipeManager: RecipeManager

    val ingredientManager: IngredientManager

    val resourceManager: ResourceManager

    /**
     * Pushes the freshly (re)loaded recipes into the platform's own recipe registry.
     *
     * Called after a runtime reload has finished parsing, which happens off the main thread. The
     * implementation is responsible for getting onto the main thread itself and for spreading the
     * work so a reload does not stall the tick loop.
     *
     * Default: nothing to do.
     */
    fun onRecipesReloaded() {
    }

}