package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.recipe.action.ResultAction
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.modifier.RecipeItemModifier
import com.wolfyscript.scafall.wrappers.minecraft.wrap
import com.wolfyscript.scafall.wrappers.world.items.ScafallItemStack
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

internal class RecipeResultImpl(
    override val choices: RecipeChoices,
    override val modifier: RecipeItemModifier,
    override val actions: List<ResultAction> = listOf(),
    override val bulkActions: List<ResultAction> = listOf(),
    override val alwaysKeepPrevious: Boolean,
) : RecipeResult {

    override fun compute(recipeEvaluationResult: RecipeEvaluationResult<*,*>, context: EvaluationContext, random: Random): ScafallItemStack {
        // A recipe whose result declares no stacks (or only tags that resolve to nothing, e.g. a tag
        // from a mod that is not installed) leaves this collection empty, and `random` throws
        // NoSuchElementException on the main thread from inside the craft event.
        val pickedChoice = choices.all().randomOrNull(random) // TODO: custom weighting?
            ?: return ItemStack.EMPTY.wrap()
        val stack = pickedChoice.create()
        modifier.modify(stack, recipeEvaluationResult, context)
        return stack
    }

    override fun runActions(context: EvaluationContext, count: Int) {
        actions.forEach {
            it.run(context, false)
        }
        if (count > 1) {
            bulkActions.forEach {
                it.run(context, true)
            }
        }
    }

    override fun toString(): String {
        return "{$choices, modified by $modifier, runs $actions and bulk $bulkActions}"
    }

}