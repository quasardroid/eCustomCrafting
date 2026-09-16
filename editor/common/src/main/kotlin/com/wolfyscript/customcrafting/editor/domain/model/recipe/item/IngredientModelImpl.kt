package com.wolfyscript.customcrafting.editor.domain.model.recipe.item

import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.editor.domain.model.recipe.IngredientConsumerModels
import com.wolfyscript.customcrafting.editor.domain.model.recipe.IngredientMatcherModels
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientConsumer
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.wrappers.world.items.ItemStackSnapshot

class CustomIngredientModelImpl(
    override val choices: RecipeChoicesModel = RecipeChoicesModelImpl(),
    override val matcher: IngredientMatcherModel<*> = IngredientMatcherModels.exact.resolveOrThrow().createEmptyModel(),
    override val consumer: IngredientConsumerModel<*> = IngredientConsumerModels.consume.resolveOrThrow().createEmptyModel(),
) : IngredientModel.CustomIngredientModel {

    companion object {

        fun loadFrom(ingredient: Ingredient): CustomIngredientModelImpl {
            val state = CustomIngredientModelImpl(
                RecipeChoicesModelImpl(
                    ingredient.choices.stacks.toMutableList(),
                    ingredient.choices.tags.toMutableList(),
                ),
            )
            // TODO: properly clone values!
            return state
        }

    }

    // No `runBlocking`: nothing in here suspends, so the wrapper only added a coroutine frame and
    // blocked whichever thread called it.
    override fun complete(): Result<Ingredient> {
        val recipeChoices = choices.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to complete Ingredient.", it))
        }
        if (recipeChoices.all().isEmpty()) {
            return Result.failure(IllegalArgumentException("Ingredient must have at least one stack or tag."))
        }
        val completedMatcher = matcher.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to complete Ingredient.", it))
        }
        val completedConsumer = consumer.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to complete Ingredient.", it))
        }

        return Result.success(Ingredient.of(recipeChoices, completedMatcher, completedConsumer))
    }

}

class SavedIngredientModelImpl(
    override val key: Key,
    override val icon: ItemStackSnapshot,
) : IngredientModel.SavedIngredientModel {

    override fun complete(): Result<Ingredient> {
        val ingredient =
            CustomCraftingProvider.get().server?.ingredientManager?.getIngredient(key) ?: return Result.failure(
                IllegalStateException("Failed to complete Ingredient: No ingredient found for key $key")
            )
        return Result.success(ingredient)
    }

}