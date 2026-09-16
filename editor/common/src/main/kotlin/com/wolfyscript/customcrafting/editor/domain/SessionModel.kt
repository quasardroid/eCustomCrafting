package com.wolfyscript.customcrafting.editor.domain

import com.wolfyscript.customcrafting.editor.domain.model.recipe.RecipeModel
import com.wolfyscript.scafall.identifier.Key

interface SessionModel {

    val recipeModel: RecipeModel<*>

    fun cancel()

    /**
     * The Recipe Editor editing an existing recipes
     */
    interface EditModel : SessionModel {

        /**
         * The key of the existing recipe
         */
        val currentKey: Key

        /**
         * Saves the edited recipe, overwriting the existing recipe.
         */
        fun save(): Result<Unit>

        /**
         * Saves the edited recipe with a new key.
         * The existing recipe is not overwritten/deleted!
         */
        fun saveAs(key: Key): Result<Unit>

    }

    /**
     * The Recipe Editor creating a new recipe
     */
    interface CreateModel : SessionModel {

        /**
         * Saves a new recipe under the specified key
         */
        fun save(key: Key): Result<Unit>

    }

}