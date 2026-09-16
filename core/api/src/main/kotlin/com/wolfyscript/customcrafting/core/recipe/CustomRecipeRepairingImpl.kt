package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditions
import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditionsImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.recipe.evaluation.RepairingRecipeDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.customcrafting.core.recipe.process.ProcessRepairing

internal class CustomRecipeRepairingImpl(
    override val priority: Int = 0,
    override val conditions: RecipeConditions = RecipeConditionsImpl(),
    override val process: ProcessRepairing,
    override val base: Ingredient,
    override val addition: Ingredient?,
    override val group: String = "",
) : CustomRecipeRepairing {

    override fun evaluate(
        input: RecipeInput.RepairingRecipeInput,
        context: EvaluationContext,
    ): RecipeEvaluationResult.RepairingRecipeData? {
        val matchedBase = base.match(input.base)?.let { baseMatch ->
            IngredientDataImpl(0, 0, base, baseMatch)
        } ?: return null

        if (addition == null && input.addition != null || addition != null && input.addition == null) {
            return null
        }
        // The addition is optional. An absent addition is a legitimate no-match-needed case, so it
        // must not abort the evaluation the way a FAILED match of a present addition does.
        var matchedAddition: IngredientDataImpl? = null
        if (addition != null) {
            val additionMatch = addition.match(input.addition!!) ?: return null
            matchedAddition = IngredientDataImpl(1, 1, addition, additionMatch)
        }

        // Conditions last — see CustomRecipeCraftingImpl.
        if (!conditions.areSatisfied(context)) {
            return null
        }

        return RepairingRecipeDataImpl(0, arrayOf(matchedBase, matchedAddition))
    }

    override fun toString(): String {
        return "Repairing ($priority) $base with $addition if $conditions producing $process"
    }

}

