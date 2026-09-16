package com.wolfyscript.customcrafting.core.recipe.evaluation

import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.scafall.items.ItemStackRef

/**
 * Holds information about an ingredient that was selected after a recipe was evaluated.
 */
interface IngredientData {

    /**
     * The slot of the ingredient in the inventory.
     * For example, the slot in the crafting grid
     */
    val invSlot: Int

    /**
     * The index of the ingredient in the recipe
     */
    val recipeIndex: Int

    /**
     * The index of the matched stack inside the **trimmed** crafting matrix
     * ([CraftingMatrixData.matrix]).
     *
     * This is NOT the same as [recipeIndex]: for a shapeless recipe the ingredient order is
     * independent of where the items sit in the grid. Use this to read the stack that was matched,
     * and [invSlot] to write it back.
     *
     * For recipe types that do not evaluate against a crafting matrix this defaults to [invSlot].
     */
    val matrixIndex: Int

    /**
     * The ingredient associated with this information
     */
    val selectedIngredient: Ingredient

    val matchedItemStackRef: ItemStackRef

}