package com.wolfyscript.customcrafting.editor.domain.model.recipe.item

import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.wrappers.world.items.ItemStackSnapshot

interface IngredientModel {

    fun complete(): Result<Ingredient>

    interface CustomIngredientModel : IngredientModel {

        val choices: RecipeChoicesModel

        val matcher: IngredientMatcherModel<*>

        val consumer: IngredientConsumerModel<*>

        // `replaceWithRemains` used to live here. It was threaded through every use-case but never
        // read when building the core Ingredient, so it silently did nothing. The remainder is
        // already fully determined by [consumer]; removed rather than left as a dead toggle.

    }

    interface SavedIngredientModel : IngredientModel {

        val key: Key

        val icon: ItemStackSnapshot

    }

}