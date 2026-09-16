package com.wolfyscript.customcrafting.editor.domain.model.recipe.item

import com.wolfyscript.customcrafting.core.recipe.modifier.RecipeItemModifier
import com.wolfyscript.customcrafting.core.recipe.RecipeResult

class ResultModelImpl(
    override val choices: RecipeChoicesModel = RecipeChoicesModelImpl(),
    override val actions: MutableList<ResultActionModel<*>> = mutableListOf(),
    override val modifier: RecipeItemModifierModel = RecipeItemModifierModelImpl(),
    override val bulkActions: MutableList<ResultActionModel<*>> = mutableListOf(),
    override var alwaysKeepPrevious: Boolean = false,
) : ResultModel {

    override fun complete(): Result<RecipeResult> {
        val recipeChoices = choices.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to complete result", it))
        }
        if (recipeChoices.all().isEmpty()) {
            return Result.failure(IllegalArgumentException("Result must have at least one stack or tag."))
        }

        val itemModifier = modifier.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to complete result", it))
        }

        // The editor's own action lists used to be dropped here, so any action configured in the
        // editor silently never made it into the saved recipe.
        val completedActions = actions.mapIndexed { index, action ->
            action.complete().getOrElse {
                return Result.failure(IllegalStateException("Failed to complete result: invalid action at index $index", it))
            }
        }
        val completedBulkActions = bulkActions.mapIndexed { index, action ->
            action.complete().getOrElse {
                return Result.failure(IllegalStateException("Failed to complete result: invalid bulk action at index $index", it))
            }
        }

        return Result.success(
            RecipeResult.of(
                choices = recipeChoices,
                modifier = itemModifier,
                actions = completedActions.toMutableList(),
                bulkActions = completedBulkActions.toMutableList(),
                alwaysKeepPrevious = alwaysKeepPrevious
            )
        )
    }
}

class RecipeItemModifierModelImpl(
    override val transformations: MutableList<TransformationModel> = mutableListOf()
) : RecipeItemModifierModel {

    override fun complete(): Result<RecipeItemModifier> {
        val completedTransformations = transformations.mapIndexed { index, transformationModel ->
            transformationModel.complete().getOrElse {
                return Result.failure(IllegalStateException("Failed to complete recipe item modifier: invalid transformation at index $index", it))
            }
        }

        return Result.success(RecipeItemModifier.of(completedTransformations))
    }

}