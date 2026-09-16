package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditions
import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditionsImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.DefaultDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientData
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientDataImpl
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.wrappers.world.items.ScafallItemStack

internal class CustomRecipeSmithingImpl(
    override val priority: Int = 0,
    override val conditions: RecipeConditions = RecipeConditionsImpl(),
    override val template: Ingredient?,
    override val base: Ingredient,
    override val addition: Ingredient?,
    override val copyOptions: CustomRecipeSmithing.CopyOptions?,
    override val result: RecipeResult,
    override val group: String = "",
) : CustomRecipeSmithing {

    override fun evaluate(
        input: RecipeInput.SmithingRecipeInput,
        context: EvaluationContext,
    ): RecipeEvaluationResult.Data? {
        if (!conditions.areSatisfied(context)) {
            return null
        }
        if (
            !validIngredient(template, input.template) ||
            (input.base == null || input.base!!.isEmpty) ||
            !validIngredient(addition, input.addition)
        ) {
            return null
        }

        // A null result means the ingredient did not match the stack. `validIngredient` above only
        // checked emptiness, so without these guards ANY three non-empty items match the recipe and
        // nothing is consumed (the nonNullIngredients list would be empty).
        val matchedTemplate = evaluateIngredient(template, input.template)
        if (template != null && matchedTemplate == null) {
            return null
        }
        val matchedBase = evaluateIngredient(base, input.base) ?: return null
        val matchedAddition = evaluateIngredient(addition, input.addition)
        if (addition != null && matchedAddition == null) {
            return null
        }

        return DefaultDataImpl(arrayOf(matchedTemplate, matchedBase, matchedAddition))
    }

    private fun validIngredient(ingredient: Ingredient?, inputStack: ScafallItemStack?): Boolean {
        val emptyStack = inputStack == null || inputStack.isEmpty
        if (ingredient == null) {
            return emptyStack
        }
        return !emptyStack
    }

    private fun evaluateIngredient(ingredient: Ingredient?, inputStack: ScafallItemStack?): IngredientData? {
        if (ingredient == null || inputStack == null || inputStack.isEmpty) {
            return null
        }
        return ingredient.match(inputStack)?.let { ingredientMatch ->
            IngredientDataImpl(0, 0, ingredient, ingredientMatch)
        }
    }

    override fun toString(): String {
        return "smithing ($priority), template=$template, base=$base, addition=$addition, copying $copyOptions, producing $result if $conditions"
    }

    data class CopyOptionsImpl(
        override val preserveComponents: List<Key> = emptyList(),
        override val excludeComponents: List<Key> = emptyList(),
    ) : CustomRecipeSmithing.CopyOptions {

        override fun toString(): String {
            return "(preserve $preserveComponents, exclude $excludeComponents)"
        }
    }

}

