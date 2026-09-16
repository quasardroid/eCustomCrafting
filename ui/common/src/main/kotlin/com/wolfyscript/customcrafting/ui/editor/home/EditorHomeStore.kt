package com.wolfyscript.customcrafting.ui.editor.home

import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.editor.EditorRegistryTypes
import com.wolfyscript.customcrafting.editor.recipeEditor
import com.wolfyscript.customcrafting.core.recipe.RecipeType
import com.wolfyscript.viewportl.gui.model.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class EditorHomeStore(val viewer: UUID) : Store() {

    internal val recipeTypeSpecificStates = EditorRegistryTypes.recipeTypeSpecificModelFactories.resolveOrThrow()

    private val homeStateFlow = MutableStateFlow(HomeState(recipeTypeSpecificStates.map { it.recipeType }))
    val homeState: StateFlow<HomeState> = homeStateFlow.asStateFlow()

    /**
     * Starts a new recipe of [recipeType] for this viewer.
     *
     * Returns the outcome so the caller can decide whether to navigate. Both failures used to be
     * swallowed here, so "you are already editing a recipe" looked like success and the view opened
     * onto a stale (or absent) model.
     */
    fun selectRecipeType(recipeType: RecipeType<*>): Result<Unit> {
        val editor = CustomCraftingProvider.get().server?.recipeEditor
            ?: return Result.failure(IllegalStateException("The recipe editor is not available"))
        val session = editor.getOrCreateSession(viewer).getOrElse { return Result.failure(it) }
        return session.create(recipeType).map { }
    }

    /**
     * Discards whatever this viewer is currently editing, so a new recipe can be started.
     */
    fun discardCurrentRecipe() {
        CustomCraftingProvider.get().server?.recipeEditor
            ?.getOrCreateSession(viewer)?.getOrNull()?.cancel()
    }

}