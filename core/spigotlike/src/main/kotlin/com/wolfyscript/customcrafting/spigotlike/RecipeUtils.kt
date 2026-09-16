package com.wolfyscript.customcrafting.spigotlike

import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.RecipeResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.IngredientData
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.scafall.spigot.api.wrappers.utils.unwrapSpigot
import com.wolfyscript.scafall.wrappers.world.items.ScafallItemStack
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import kotlin.random.Random

/**
 * How many times the recipe can be crafted with what is currently in the input slots.
 *
 * [sourceStackFor] must resolve the stack that the given ingredient actually matched. Pairing the
 * ingredient list with the grid stacks BY POSITION is wrong for shapeless recipes, where the recipe
 * order and the placement order are unrelated: it divided each stack by another ingredient's
 * required amount and made the recipe uncraftable depending on where the player dropped the items.
 */
fun possibleResultAmount(
    recipeEvaluationResult: RecipeEvaluationResult<*, *>,
    sourceStackFor: (IngredientData) -> ScafallItemStack?,
): Int {
    var maxPossible = Int.MAX_VALUE
    for (ingredient in recipeEvaluationResult.data.nonNullIngredients) {
        val required = ingredient.matchedItemStackRef.amount
        if (required <= 0) {
            continue
        }
        val available = sourceStackFor(ingredient) ?: return 0
        val possible = available.amount / required
        if (possible < maxPossible) {
            maxPossible = possible
        }
    }
    // An empty ingredient list used to throw out of `minOf` inside the click event.
    return if (maxPossible == Int.MAX_VALUE) 0 else maxPossible
}

fun collectResultAndRunActions(
    event: InventoryClickEvent,
    targetInv: Inventory,
    craftingData: RecipeEvaluationResult<*, *>,
    recipeResult: RecipeResult,
    sourceStackFor: (IngredientData) -> ScafallItemStack?,
    context: EvaluationContext,
    random: Random,
    /**
     * Callers that go on to invoke `CustomRecipe.shrink` must pass false: `shrink` already runs the
     * result actions, and running them here as well paid every reward twice per craft.
     */
    runResultActions: Boolean = true,
): Int {
    var maxPossible = possibleResultAmount(craftingData, sourceStackFor)

    if (!event.isShiftClick) {
        if (maxPossible <= 0) {
            return 0
        }
        val result = recipeResult.compute(craftingData, context, random).unwrapSpigot()

        val cursor = event.cursor
        if (cursor.type == Material.AIR || (result.isSimilar(cursor) && cursor.amount + result.amount <= cursor.maxStackSize)) {
            if (cursor.type == Material.AIR) {
                event.view.setCursor(result)
            } else {
                cursor.amount += result.amount
            }
            if (runResultActions) {
                recipeResult.runActions(context, 1)
            }
            return 1
        }
        return 0
    }

    if (event.isShiftClick) {
        maxPossible = quickCraft(maxPossible, targetInv, craftingData, recipeResult, context, random)
        if (runResultActions) {
            recipeResult.runActions(context, maxPossible)
        }
        return maxPossible
    }
    return 0
}

fun quickCraft(
    maxPossible: Int,
    targetInv: Inventory,
    craftingData: RecipeEvaluationResult<*, *>,
    recipeResult: RecipeResult,
    context: EvaluationContext,
    random: Random,
): Int {
    for (i in 0..<maxPossible) {
        val stack = recipeResult.compute(craftingData, context, random).unwrapSpigot()
        val stackCopy = stack.clone()
        val remains = targetInv.addItem(stackCopy)
        if (remains.isNotEmpty()) {
            // revert the last added stack again, by removing what was not added
            val toRemove = stack.amount - remains[0]!!.amount
            stack.amount = toRemove
            targetInv.removeItem(stack)
            return i
        }
    }
    return maxPossible
}
