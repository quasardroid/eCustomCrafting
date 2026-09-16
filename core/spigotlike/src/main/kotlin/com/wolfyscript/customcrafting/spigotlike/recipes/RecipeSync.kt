package com.wolfyscript.customcrafting.spigotlike.recipes

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.recipe.RecipeReference
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.scheduler.Delay
import com.wolfyscript.scafall.scheduler.Task
import com.wolfyscript.scafall.scheduler.Timer
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.inventory.Recipe
import java.util.concurrent.atomic.AtomicReference

/**
 * How many recipes are pushed into the Bukkit registry per tick.
 *
 * Each `addRecipe`/`removeRecipe` mutates the server's recipe map, so the whole batch has to run on
 * the main thread. Spreading it keeps any single tick well inside the 50 ms budget instead of doing
 * everything in one frame.
 */
private const val RECIPES_PER_TICK = 200

/**
 * The placeholder/display recipe keys CustomCrafting currently has registered with Bukkit.
 *
 * Needed so a reload can REMOVE the platform recipes of custom recipes that no longer exist —
 * nothing tracked them before, so a deleted recipe kept working in the crafting book until restart.
 */
private val registeredKeys = AtomicReference<Set<NamespacedKey>>(emptySet())

/**
 * The sync task currently draining a batch, if any. A second reload started while the first is still
 * pushing recipes must replace it, not run alongside it.
 */
private val activeSyncTask = AtomicReference<Task?>(null)

/**
 * Rebuilds the Bukkit-side placeholder and display recipes to match [recipes].
 *
 * The building runs on the CALLING thread (intended: the async reload thread) because constructing
 * `ShapedRecipe`/`ShapelessRecipe` objects and materialising every ingredient choice is the
 * expensive part and touches no server state. Only the registry mutation is scheduled onto the main
 * thread, in [RECIPES_PER_TICK]-sized slices, and the clients are resynced exactly once at the end.
 */
fun syncPlatformRecipes(
    customCrafting: CustomCrafting,
    recipes: Collection<RecipeReference<*>>,
    onComplete: (registered: Int) -> Unit = {},
) {
    val settings = customCrafting.configurationManager.recipeBookSettings
    if (!settings.registerPlaceholders) {
        customCrafting.logger.info(
            "[Recipes] Platform recipe registration is disabled (recipe_book.conf: registerPlaceholders = false); " +
                    "custom cooking and stonecutting recipes will not work."
        )
        // Anything registered by an earlier run still has to go, or it would linger until restart.
        dropAllRegistered(customCrafting, settings.syncToPlayers)
        onComplete(0)
        return
    }

    val built: List<Recipe> = buildPlaceholderRecipes(customCrafting, recipes) + buildDisplayRecipes(recipes)
    val newKeys = built.mapTo(HashSet()) { (it as Keyed).key }
    val staleKeys = registeredKeys.get() - newKeys

    val pending = ArrayDeque(built)
    val toRemove = ArrayDeque(staleKeys)
    val taskRef = AtomicReference<Task?>(null)

    // Supersede a sync that is still draining from an earlier reload.
    activeSyncTask.getAndSet(null)?.cancel()

    val task = ScafallProvider.get().scheduler.sync(customCrafting, Delay.amount(1), Timer.forever(1)) {
        if (pending.isEmpty() && toRemove.isEmpty()) {
            taskRef.get()?.cancel()
            return@sync
        }

        var budget = RECIPES_PER_TICK
        while (budget > 0 && toRemove.isNotEmpty()) {
            Bukkit.removeRecipe(toRemove.removeFirst(), false)
            budget--
        }
        while (budget > 0 && pending.isNotEmpty()) {
            val recipe = pending.removeFirst()
            val key = (recipe as Keyed).key
            if (Bukkit.getRecipe(key) != null) {
                Bukkit.removeRecipe(key, false)
            }
            Bukkit.addRecipe(recipe, false)
            budget--
        }

        if (pending.isEmpty() && toRemove.isEmpty()) {
            // One resync for the whole batch, instead of two per recipe — and none at all when the
            // recipe book is switched off, which is the single most expensive packet here.
            if (settings.syncToPlayers) {
                Bukkit.updateRecipes()
            }
            registeredKeys.set(newKeys)
            customCrafting.logger.info(
                "[Recipes] Synchronised ${built.size} platform recipes (${staleKeys.size} removed)"
            )
            onComplete(built.size)
            taskRef.get()?.cancel()
            activeSyncTask.compareAndSet(taskRef.get(), null)
        }
    }
    taskRef.set(task)
    activeSyncTask.set(task)
}

/**
 * Unregisters everything CustomCrafting previously put into the Bukkit recipe registry.
 *
 * Used when `registerPlaceholders` is switched off: without it, the recipes registered before the
 * setting changed would stay live until the next restart.
 */
private fun dropAllRegistered(customCrafting: CustomCrafting, syncToPlayers: Boolean) {
    val previous = registeredKeys.getAndSet(emptySet())
    if (previous.isEmpty()) {
        return
    }
    ScafallProvider.get().scheduler.sync(customCrafting) {
        for (key in previous) {
            Bukkit.removeRecipe(key, false)
        }
        if (syncToPlayers) {
            Bukkit.updateRecipes()
        }
        customCrafting.logger.info("[Recipes] Removed ${previous.size} previously registered platform recipes")
    }
}

/**
 * Records the keys registered by the initial (startup) registration, so the first reload knows what
 * it may have to remove.
 */
internal fun rememberRegisteredKeys(customCrafting: CustomCrafting, recipes: Collection<RecipeReference<*>>) {
    val keys = (buildPlaceholderRecipes(customCrafting, recipes) + buildDisplayRecipes(recipes))
        .mapTo(HashSet()) { (it as Keyed).key }
    registeredKeys.set(keys)
}
