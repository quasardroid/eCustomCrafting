package com.wolfyscript.customcrafting.editor.domain.model.recipe.item

import com.wolfyscript.customcrafting.core.recipe.RecipeResult

interface ResultModel {

    val choices: RecipeChoicesModel

    val actions: List<ResultActionModel<*>>

    val bulkActions: List<ResultActionModel<*>>

    val modifier: RecipeItemModifierModel

    /**
     * Mirrors [RecipeResult.alwaysKeepPrevious]; without it the editor could only ever produce
     * results with the default value.
     */
    val alwaysKeepPrevious: Boolean

    fun complete(): Result<RecipeResult>

}