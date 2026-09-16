package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.recipe.evaluation.DefaultDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientData
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient

internal class CraftingFormulaShapelessImpl(
    override val ingredients: List<Ingredient>,
) : CraftingFormula.Shapeless {

    override fun evaluate(
        input: RecipeInput.CraftingRecipeInput,
        recipeCrafting: CustomRecipeCrafting,
    ): RecipeEvaluationResult.Data? {
        if (input.matrixData.flatItems.size != ingredients.size) {
            return null
        }
        val pickedIngredients = Array<IngredientData?>(ingredients.size) { null }

        // Assign every grid item to a distinct ingredient by plain backtracking.
        //
        // The previous implementation memoised (previousIngredient -> nextIngredient) edges in a
        // single matrix that was never cleared on backtrack. Whether such an edge is viable depends
        // on the whole prefix, not just the previous node, so a pair rejected deep in one branch was
        // permanently banned in every other branch and craftable grids were reported as no-match.
        //
        // Here the memo is keyed by the SET of already-consumed ingredients, which is exactly the
        // state the remaining sub-problem depends on, so it is sound. With at most 9 ingredients the
        // table is at most 512 entries and the search is bounded by O(2^n * n).
        val failedStates = BooleanArray(1 shl ingredients.size)
        if (!assignFrom(0, 0, input, pickedIngredients, failedStates)) {
            return null
        }
        return DefaultDataImpl(pickedIngredients)
    }

    /**
     * Tries to assign the grid item at [itemIndex] (and every item after it) to an ingredient that
     * is not yet part of [usedIngredients].
     */
    private fun assignFrom(
        itemIndex: Int,
        usedIngredients: Int,
        input: RecipeInput.CraftingRecipeInput,
        pickedIngredients: Array<IngredientData?>,
        failedStates: BooleanArray,
    ): Boolean {
        if (itemIndex == ingredients.size) {
            return true
        }
        if (failedStates[usedIngredients]) {
            return false
        }
        val stack = input.matrixData.flatItems[itemIndex]
        for ((ingrdRecipeIndex, ingredient) in ingredients.withIndex()) {
            val ingredientBit = 1 shl ingrdRecipeIndex
            if (usedIngredients and ingredientBit != 0) {
                continue
            }
            val matchedRef = ingredient.match(stack) ?: continue
            pickedIngredients[ingrdRecipeIndex] = IngredientDataImpl(
                invSlot = input.matrixData.flatItemIndices[itemIndex],
                recipeIndex = ingrdRecipeIndex,
                selectedIngredient = ingredient,
                matchedItemStackRef = matchedRef,
                matrixIndex = input.matrixData.flatMatrixIndices[itemIndex],
            )
            if (assignFrom(itemIndex + 1, usedIngredients or ingredientBit, input, pickedIngredients, failedStates)) {
                return true
            }
            pickedIngredients[ingrdRecipeIndex] = null
        }
        failedStates[usedIngredients] = true
        return false
    }

    override fun toString(): String {
        return "shapeless $ingredients"
    }

}