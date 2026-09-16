package com.wolfyscript.customcrafting.spigotlike.recipes

import com.wolfyscript.customcrafting.core.recipe.CraftingFormula
import com.wolfyscript.customcrafting.core.recipe.CustomRecipeCrafting
import com.wolfyscript.customcrafting.core.recipe.RecipeReference
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.spigot.api.wrappers.utils.unwrapSpigot
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.inventory.CraftingRecipe
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe

const val DISPLAY_RECIPE_PREFIX = "cc_display."

/**
 * Builds the Bukkit display recipes for [recipes]. Pure data; safe off the main thread.
 */
fun buildDisplayRecipes(recipes: Collection<RecipeReference<*>>): List<Recipe> {
    return recipes.mapNotNull { it.toDisplay() }
}

fun Recipe.isDisplay(): Boolean {
    return (this as Keyed).key.key.startsWith(DISPLAY_RECIPE_PREFIX)
}

fun Key.toDisplayRecipeKey(): NamespacedKey {
    return NamespacedKey(this.namespace, "$DISPLAY_RECIPE_PREFIX${this.value}")
}

/**
 * NOTE: display recipes are currently INERT — this always returns null, because the `when` result is
 * discarded and the function falls through to `return null`.
 *
 * It is deliberately left that way rather than "fixed" here: [CustomRecipeCrafting.toDisplay] below
 * builds its key with `toPlaceholderRecipeKey()` instead of [toDisplayRecipeKey], so simply returning
 * the value would make every display recipe overwrite the placeholder registered under the same key.
 * Wire both up together.
 */
fun RecipeReference<*>.toDisplay(): Recipe? {
    when (val recipe = value) {
        is CustomRecipeCrafting -> recipe.toDisplay(key)
    }
    return null
}

fun CustomRecipeCrafting.toDisplay(key: Key): CraftingRecipe? {
    when (val formula = this.formula) {
        is CraftingFormula.Shaped -> {
            val recipe = ShapedRecipe(key.toPlaceholderRecipeKey(), result.choices.all().first().create().unwrapSpigot())
            recipe.shape(*formula.shape.rows.toTypedArray())

            for ((index, ingredientKey) in formula.shape.ingredientIndices.withIndex()) {
                val ingredient = formula.ingredients[index]
                recipe.setIngredient(
                    ingredientKey,
                    RecipeChoice.ExactChoice(ingredient.choices.all().map { it.create().unwrapSpigot() })
                )
            }
            return recipe
        }

        is CraftingFormula.Shapeless -> {
            val recipe = ShapelessRecipe(key.toPlaceholderRecipeKey(), result.choices.all().first().create().unwrapSpigot())
            for (ingredient in formula.ingredients) {
                recipe.addIngredient(
                    RecipeChoice.ExactChoice(
                        ingredient.choices.all().map { it.create().unwrapSpigot() })
                )
            }

            return recipe
        }

        else -> {
            return null
        }
    }
}
