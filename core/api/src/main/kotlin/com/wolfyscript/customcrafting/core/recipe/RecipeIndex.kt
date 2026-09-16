package com.wolfyscript.customcrafting.core.recipe

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableMap
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.resource.LoadedObject
import com.wolfyscript.scafall.identifier.Key
import java.util.*

/**
 * The internal index of all the custom recipes.
 * Recipes are indexed by their [Key] and [RecipeType].
 *
 * This index is immutable, and should be updated using [registerOrUpdateAll] and [removeBatch].
 * That way, the index is thread-safe and prevents concurrent modification issues.
 *
 * [RecipeReferences][RecipeReference] are used to prevent caching of the recipe objects directly and causing memory leaks, because the objects may be removed/updated at any time.
 */
internal class RecipeIndex {

    companion object {
        /**
         * Orders the per-type recipe list by DESCENDING priority.
         *
         * [RecipeManagerCommon.evaluateRecipesOfType] returns the first match, and
         * [CustomRecipe.priority] is documented as "recipes of higher priority are checked before
         * recipes of lower priority" — a plain ascending `comparing` made the lowest priority win,
         * so the documented way to override a recipe never fired.
         */
        private val recipeValueComparator: Comparator<RecipeReference<*>> =
            Comparator.comparing<RecipeReference<*>, Int> { it.value?.priority ?: 0 }.reversed()
    }

    private val recipes: List<CustomRecipe<*, *>>
    // reference those recipes, but do not hold on to those object references directly
    internal val byKey: Map<Key, RecipeReference<*>>
    internal val byType: ImmutableListMultimap<RecipeType<*>, RecipeReference<*>>

    constructor(recipes: Collection<LoadedObject<CustomRecipe<*, *>>>) {
        val recipesBuilder = ImmutableList.builder<CustomRecipe<*, *>>()
        val byKeyBuilder = ImmutableMap.builder<Key, RecipeReference<*>>()
        val byTypeBuilder = ImmutableListMultimap.Builder<RecipeType<*>, RecipeReference<*>>()
        byTypeBuilder.orderValuesBy(recipeValueComparator)

        recipes.forEach {
            recipesBuilder.add(it.value)
            val ref = RecipeReference.of(it.key, it.value)
            byTypeBuilder.put(it.value.type, ref)
            byKeyBuilder.put(it.key, ref)
        }

        this.byType = byTypeBuilder.build()
        this.byKey = byKeyBuilder.build()
        this.recipes = recipesBuilder.build()
    }

    constructor(recipes: List<CustomRecipe<*, *>>, byKey: Map<Key, RecipeReference<*>>, byType: ImmutableListMultimap<RecipeType<*>, RecipeReference<*>>) {
        this.recipes = recipes
        this.byKey = byKey
        this.byType = byType
    }

    fun values(): Collection<RecipeReference<*>> {
        return Collections.unmodifiableCollection(byKey.values)
    }

    fun get(key: Key): RecipeReference<*>? = synchronized(this) {
        return byKey[key]
    }

    fun registerOrUpdateAll(recipes: Collection<LoadedObject<CustomRecipe<*,*>>>) : RecipeIndex {
        val updatedRecipes = ArrayList<CustomRecipe<*,*>>(recipes.size + this.recipes.size)
        val byKeyBuilder = ImmutableMap.builder<Key, RecipeReference<*>>()
        val byTypeBuilder = ImmutableListMultimap.Builder<RecipeType<*>, RecipeReference<*>>()
        byTypeBuilder.orderValuesBy(recipeValueComparator)

        updatedRecipes.addAll(this.recipes)
        val updatedByKey = byKey.toMutableMap()
        val newRefs = LinkedHashMap<Key, RecipeReference<*>>(recipes.size)
        for (recipe in recipes) {
            // Drop the superseded entry first: ImmutableMap.Builder throws on duplicate keys.
            val existing = updatedByKey.remove(recipe.key)
            updatedRecipes.remove(existing?.value)
            updatedRecipes.add(recipe.value)

            newRefs[recipe.key] = RecipeReference.of(recipe.key, recipe.value)
        }

        // Carry the surviving recipes over. Without this, the rebuilt lookup maps hold ONLY the
        // recipes passed to this call, so registering a single recipe silently unregistered every
        // other recipe on the server while `recipes` still listed them.
        for ((key, ref) in updatedByKey) {
            byKeyBuilder.put(key, ref)
            ref.value?.let { byTypeBuilder.put(it.type, ref) }
        }
        for ((key, ref) in newRefs) {
            byKeyBuilder.put(key, ref)
            ref.value?.let { byTypeBuilder.put(it.type, ref) }
        }

        return RecipeIndex(updatedRecipes, byKeyBuilder.build(), byTypeBuilder.build())
    }

    fun removeBatch(vararg keys: Key) : RecipeIndex {
        val updatedByKey = byKey.toMutableMap()
        for (key in keys) {
            updatedByKey.remove(key)
        }
        val recipesBuilder = ImmutableList.builder<CustomRecipe<*, *>>()
        val byTypeBuilder = ImmutableListMultimap.Builder<RecipeType<*>, RecipeReference<*>>()
        byTypeBuilder.orderValuesBy(recipeValueComparator)
        updatedByKey.keys.forEach { key ->
            val ref = byKey[key]
            ref?.value?.let { recipe ->
                recipesBuilder.add(recipe)
                val ref = RecipeReference.of(key, recipe)
                byTypeBuilder.put(recipe.type, ref)
            }
        }
        return RecipeIndex(recipesBuilder.build(), updatedByKey.toMap(), byTypeBuilder.build())
    }

    fun <I : RecipeInput, D : RecipeEvaluationResult.Data, T : CustomRecipe<I, D>> byType(type: RecipeType<T>): Collection<RecipeReference<T>> {
        return byType.get(type) as Collection<RecipeReference<T>>
    }

}