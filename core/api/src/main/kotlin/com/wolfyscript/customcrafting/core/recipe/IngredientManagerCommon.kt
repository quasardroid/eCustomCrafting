package com.wolfyscript.customcrafting.core.recipe

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientManager.Companion.LOG_PREFIX
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import com.wolfyscript.customcrafting.core.recipe.ingredient.IngredientManager
import com.wolfyscript.customcrafting.core.resource.DataType
import com.wolfyscript.customcrafting.core.resource.LoadedObject
import com.wolfyscript.customcrafting.core.resource.ResourceLoader
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.identifier.Key
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

internal class IngredientManagerCommon(val customCrafting: CustomCrafting) : IngredientManager {

    private val ingredients: BiMap<Key, Ingredient> = HashBiMap.create()
    private val ingredientsLoadedByCC: MutableSet<Key> = ObjectOpenHashSet()

    private val ingredientsAwaitingDependencies: MutableList<LoadedObject<Ingredient>> = mutableListOf()

    val scafall = ScafallProvider.get()

    init {
        scafall.dependencyManager.onDependencyInitialized {
            verifyIngredientsAndLoad()
        }
    }

    override fun registerIngredient(
        key: Key,
        ingredient: Ingredient,
    ) {
        if (ingredients.containsKey(key)) {
            error("Ingredient with key $key already exists.")
        }
        if (ingredients.containsValue(ingredient)) {
            error("Ingredient $ingredient is already registered under ${ingredients.inverse()[ingredient]}.")
        }
        this.ingredients[key] = ingredient
    }

    override fun getIngredient(key: Key): Ingredient? {
        return this.ingredients[key]
    }

    override fun getKey(ingredient: Ingredient): Key? {
        return this.ingredients.inverse()[ingredient]
    }

    override fun onInitialLoad(resourceLoader: ResourceLoader) {
        // Each load cycle rebuilds this list; see RecipeManagerCommon for the same reasoning.
        ingredientsAwaitingDependencies.clear()
        resourceLoader.sources.forEach { source ->
            source.load(DataType.Ingredients) {
                customCrafting.logger.debug("{}loaded: {} -> {}", LOG_PREFIX, it.key, it.value)
                ingredientsAwaitingDependencies.add(it)
            }
        }
    }

    override fun onReload(resourceLoader: ResourceLoader) {
        onInitialLoad(resourceLoader)
    }

    override fun onFinalize(resourceLoader: ResourceLoader) {
        // Snapshot, do not alias: `clear()` on the next line would otherwise empty this too and
        // `subtract` below would always yield nothing, so deleted ingredients stayed registered.
        val previouslyLoaded = this.ingredientsLoadedByCC.toSet()
        this.ingredientsLoadedByCC.clear()

        verifyIngredientsAndLoad()

        val removed = previouslyLoaded.subtract(ingredientsLoadedByCC)
        if (removed.isNotEmpty()) {
            customCrafting.logger.info("${LOG_PREFIX}Removing ${removed.size} ingredients that are no longer loaded")
            removed.forEach { ingredients.remove(it) }
        }
    }

    private fun verifyIngredientsAndLoad() {
        customCrafting.logger.info("${LOG_PREFIX}Registering ${ingredientsAwaitingDependencies.size} ingredients")
        for (loadedIngredient in ingredientsAwaitingDependencies) {
            // Idempotent on purpose: this method runs both from the onDependencyInitialized hook and
            // again from onFinalize over the SAME queue. Dropping the previous entry first turns the
            // second pass into a replace; otherwise registerIngredient reports a duplicate key and
            // throws, aborting every ingredient after it.
            // The queue itself must NOT be drained here — onFinalize rebuilds ingredientsLoadedByCC
            // from it to work out which ingredients disappeared.
            ingredients.remove(loadedIngredient.key)
            registerIngredient(loadedIngredient.key, loadedIngredient.value)
            ingredientsLoadedByCC.add(loadedIngredient.key)
        }
    }

}