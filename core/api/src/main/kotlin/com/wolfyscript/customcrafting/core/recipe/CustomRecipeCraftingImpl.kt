package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditions
import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditionsImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.scafall.wrappers.world.items.ScafallItemStack

internal class CustomRecipeCraftingImpl(
    override val priority: Int = 0,
    override val conditions: RecipeConditions = RecipeConditionsImpl(),
    override val formula: CraftingFormula,
    override val result: RecipeResult,
    override val group: String = "",
) : CustomRecipeCrafting {

    override fun evaluate(
        input: RecipeInput.CraftingRecipeInput,
        context: EvaluationContext,
    ): RecipeEvaluationResult.Data? {
        // Structural match FIRST. `evaluateRecipesOfType` scans every recipe of the type on every
        // change to the grid, and the formula check is a cheap dimension/ingredient compare, while
        // conditions are user-configured predicates (permission, world, ...) that can be arbitrarily
        // expensive. Evaluating conditions for recipes whose shape cannot even fit the grid was the
        // dominant cost of that scan. Both must hold, so the order does not change the outcome.
        val data = formula.evaluate(input, this) ?: return null
        if (!conditions.areSatisfied(context)) {
            return null
        }
        return data
    }

    override fun shrink(
        input: RecipeInput.CraftingRecipeInput,
        recipeEvaluationResult: RecipeEvaluationResult<RecipeEvaluationResult.Data, CustomRecipeCrafting>,
        context: EvaluationContext,
        count: Int,
        applyStacks: (Int, ScafallItemStack) -> Unit,
    ) {
        result.runActions(context, count)

        for (value in recipeEvaluationResult.data.nonNullIngredients) {
            // Read by matrix position and write back by inventory slot. `recipeIndex` is the position
            // in the recipe's ingredient list, which for a shapeless recipe has nothing to do with
            // where the item sits in the grid; using it here shrank one stack and stored it over
            // another, destroying items.
            var stack = input.matrixData.matrix[value.matrixIndex]
            stack = value.selectedIngredient.shrink(
                stack,
                count,
                value.matchedItemStackRef,
                context,
                recipeEvaluationResult
            )
            applyStacks(value.invSlot, stack)
        }
    }

    override fun toString(): String {
        return "crafting ($priority) with $formula producing $result if $conditions"
    }

}

