package com.wolfyscript.customcrafting.editor.domain.model

import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.editor.domain.SessionModel
import com.wolfyscript.customcrafting.editor.domain.model.recipe.RecipeModel
import com.wolfyscript.customcrafting.core.recipe.CustomRecipe
import com.wolfyscript.customcrafting.core.resource.DataType
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.identifier.Key

private fun saveRecipe(key: Key, recipe: CustomRecipe<*,*>): Result<Boolean> {
    val resourceLoader = CustomCraftingProvider.get().server?.resourceManager?.resourceLoader
        ?: return Result.failure(IllegalStateException("No resource loader available; is the server initialised?"))
    val result = resourceLoader.save(DataType.Recipes, key, recipe)
    // Only claim the save happened when it actually did.
    result.fold(
        onSuccess = { ScafallProvider.get().logger.info("[RecipeManager] Saved $key with $recipe") },
        onFailure = { ScafallProvider.get().logger.error("[RecipeManager] Failed to save $key", it) },
    )
    return result
}

internal class EditRecipeSessionModel(key: Key, override val recipeModel: RecipeModel<*>) : SessionModel.EditModel {

    override var currentKey: Key = key
        private set

    override fun saveAs(key: Key): Result<Unit> {
        val result = recipeModel.complete()
        val recipe = result.getOrElse {
            ScafallProvider.get().logger.error("Failed to save recipe: ", it)
            return Result.failure(it)
        }
        currentKey = key
        // Propagate the outcome so the caller can tell the player the truth.
        return saveRecipe(key, recipe).map { }
        // TODO: update recipe manager? or require to manually reload later?
    }

    override fun save(): Result<Unit> {
        return saveAs(currentKey)
    }

    override fun cancel() {


    }

}

internal class CreateRecipeSessionModel(override val recipeModel: RecipeModel<*>) : SessionModel.CreateModel {

    override fun save(key: Key): Result<Unit> {
        val result = recipeModel.complete()
        val recipe = result.getOrElse {
            ScafallProvider.get().logger.error("Failed to save recipe: ", it)
            return Result.failure(it)
        }
        return saveRecipe(key, recipe).map { }
    }

    override fun cancel() {

    }

}