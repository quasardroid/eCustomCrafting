package com.wolfyscript.customcrafting.fabric.inject

import com.wolfyscript.scafall.identifier.Key
import net.minecraft.server.level.ServerPlayer
import kotlin.random.Random

/**
 * Caches the seed for a given recipe to produce the same result.
 *
 * There are two caches:
 * - A persistent cache that is used for all results that require a persistent seed. (Result cannot be rerolled without picking up the result)
 * - A temporary cache for all other recipe results. (Result can be rerolled when the number of recipes crafted exceeds the capacity)
 *
 */
class RecipeResultStateCache {

    private val capacity = 60

    private val persistentCache = mutableMapOf<Key, Entry>()
    // `capacity` is the eviction bound, NOT the expected size. Pre-sizing the backing array to 60
    // meant every furnace block entity in the world paid for 60 references it will almost never use.
    private val tempCache = ArrayList<Entry>()

    fun get(recipeKey: Key, persistent: Boolean) : Entry {
        if (persistent) {
            return persistentCache.getOrPut(recipeKey) { Entry(recipeKey, Random.nextLong()) }
        }

        var found = tempCache.find { it.recipeKey == recipeKey } // Simply use a linear search, since size is limited and small.
        if (found == null) {
            found = Entry(recipeKey, Random.nextLong())
            if (tempCache.size >= capacity) {
                tempCache.removeLast()
            }
            tempCache.add(0, found)
        } else {
            // Move to the front on a HIT too. Without this the list is insertion-ordered, not LRU,
            // so a frequently reused entry still drifted to the tail and got evicted while stale
            // one-off entries survived — exactly the opposite of what the capacity bound is for.
            tempCache.remove(found)
            tempCache.add(0, found)
        }
        return found
    }

    fun reset(recipeKey: Key) {
        persistentCache.remove(recipeKey)
        tempCache.removeAll { it.recipeKey == recipeKey }
    }

    data class Entry(val recipeKey: Key, val seed: Long) {

        val random
            get() = Random(seed)

    }

}

fun ServerPlayer.getRecipeResultCache(recipeKey: Key, persistent: Boolean) : RecipeResultStateCache.Entry {
    return (this as RecipeResultCacheExt).`customcrafting$getRecipeResultStateCache`().get(recipeKey, persistent)
}

fun ServerPlayer.getRecipeResultCachedRandom(recipeKey: Key, persistent: Boolean) : Random {
    return (this as RecipeResultCacheExt).`customcrafting$getRecipeResultStateCache`().get(recipeKey, persistent).random
}

fun ServerPlayer.resetRecipeResult(recipeKey: Key) {
    (this as RecipeResultCacheExt).`customcrafting$getRecipeResultStateCache`().reset(recipeKey)
}