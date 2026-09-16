package com.wolfyscript.customcrafting.spigotlike.recipes

import com.github.benmanes.caffeine.cache.Caffeine
import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.recipe.CustomRecipeGrinding
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.RecipeTypes
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.recipe.process.ProcessGrinding
import com.wolfyscript.customcrafting.spigotlike.RecipeSeeds
import com.wolfyscript.scafall.spigot.api.wrappers.utils.toPreciseGlobal
import com.wolfyscript.scafall.spigot.api.wrappers.utils.unwrapSpigot
import com.wolfyscript.scafall.spigot.api.wrappers.utils.wrap
import org.bukkit.Material
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.GrindstoneInventory
import org.bukkit.persistence.PersistentDataType
import java.time.Duration
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GrindstoneListener(val customCrafting: CustomCrafting) : Listener {

    private val recipeCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(1_000)
        .build<UUID, RecipeEvaluationResult<RecipeEvaluationResult.GrindingRecipeData, CustomRecipeGrinding>>()

    @EventHandler
    fun onCollectResult(event: InventoryClickEvent) {
        val inventory = event.clickedInventory
        if (inventory == null || event.action == InventoryAction.NOTHING || (inventory.type != InventoryType.GRINDSTONE)) {
            return
        }
        if (event.slotType != InventoryType.SlotType.RESULT) {
            return
        }

        val result = event.currentItem
        val cursor = event.cursor

        val player = event.whoClicked as Player
        val data = recipeCache.getIfPresent(player.uniqueId) ?: return
        val recipe = data.recipe.value ?: return

        event.isCancelled = true // Block vanilla behaviour of just removing the entire input
        if (result == null) {
            return
        }

        if (event.isShiftClick) {
            // TODO: manual result collection
            val notAdded = event.view.bottomInventory.addItem(result.clone()).get(0)
            if (notAdded != null) {
                val toReverse = result.amount - notAdded.amount
                notAdded.amount = toReverse
                event.view.bottomInventory.removeItem(notAdded)
                return
            }
            event.currentItem = null
        } else if (cursor.type == Material.AIR) {
            event.view.setCursor(result.clone())
            event.currentItem = null
        } else if (cursor.isSimilar(result)) {
            // The test used to be inverted: it merged the result onto a cursor holding a DIFFERENT
            // item, and when the cursor actually could be merged no branch ran at all, so the
            // ingredients below were consumed without the player collecting anything.
            val increasedAmount = cursor.amount + result.amount
            if (increasedAmount > cursor.maxStackSize) {
                return
            }
            cursor.amount = increasedAmount
            event.currentItem = null
        } else {
            return // cursor holds a different item; the result cannot be collected
        }

        val context =
            EvaluationContext.of((event.view.player as Player).wrap(), event.inventory.location?.toPreciseGlobal())

        val totalYield = max(0, data.data.yield - data.data.penalty)

        if (totalYield > 0) {
            player.location.world.spawn(player.location, ExperienceOrb::class.java).apply {
                experience = totalYield
            }
        }

        if (recipe.process is ProcessGrinding.FixedResultProcessGrinding) {
            (recipe.process as ProcessGrinding.FixedResultProcessGrinding).result.runActions(context)
        }

        // TODO: Craft remains
        data.data.bySlot(0)?.let {
            inventory.getItem(0)?.apply {
                amount -= it.matchedItemStackRef.amount
            }
        }

        data.data.bySlot(1)?.let {
            inventory.getItem(1)?.apply {
                amount -= it.matchedItemStackRef.amount
            }
        }

        recipeCache.invalidate(player.uniqueId)
        player.persistentDataContainer.set(RecipeSeeds.playerGrindingSeedKey, PersistentDataType.LONG, Random.nextLong())
    }

    @EventHandler
    fun onPrepare(event: PrepareGrindstoneEvent) {
        recipeCache.invalidate(event.view.player.uniqueId)

        val context =
            EvaluationContext.of((event.view.player as Player).wrap(), event.inventory.location?.toPreciseGlobal())
        val input =
            RecipeInput.GrindingRecipeInput.of(event.inventory.getItem(0)?.wrap(), event.inventory.getItem(1)?.wrap())

        val data = customCrafting.server!!.recipeManager.evaluateRecipesOfType(RecipeTypes.grinding.resolveOrThrow(), input, context) ?: return // Not a custom recipe
        val recipe = data.recipe.value ?: return

        event.result = recipe.process.compute(data, input, context,
            Random(getGrindingSeed(event.view.player as Player))
        ).unwrapSpigot()

        recipeCache.put(event.view.player.uniqueId, data)
    }

    @EventHandler
    fun onClickIngredient(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        if (topInventory !is GrindstoneInventory) {
            return
        }
        if (event.slot == 2 || event.slotType == InventoryType.SlotType.RESULT) {
            return // Ignore result slot
        }
        event.whoClicked as Player
        event.action

        val currentItem = event.currentItem
        val cursor = event.cursor

        if ((currentItem == null || currentItem.type == Material.AIR) && cursor.type == Material.AIR) {
            return
        }

        if (event.clickedInventory == topInventory) {
            // Place items into slot
            if (event.slot == 2 || event.slotType == InventoryType.SlotType.RESULT) {
                return // Do not place into result slot
            }

            if (cursor.type == Material.AIR || cursor.amount <= 0) {
                return // do not care about picking up items. that should work by default.
            }

            if (event.isLeftClick) {
                if (currentItem != null && currentItem.isSimilar(cursor)) {
                    // placing cursor into slot
                    val curAmount = currentItem.amount
                    val possible = min(cursor.amount, currentItem.maxStackSize - curAmount)
                    event.currentItem!!.amount += possible
                    // `min(0, ...)` is never positive and wiped whatever was left on the cursor.
                    event.cursor.amount = max(0, cursor.amount - possible)
                    event.isCancelled = true
                    return
                }

                // Swap cursor and item in slot
                val copyCurrent = event.currentItem?.clone()
                event.currentItem = cursor.clone()
                event.view.setCursor(copyCurrent)
                event.isCancelled = true
                return
            }

            if (event.isRightClick) {
                if (currentItem == null || currentItem.type == Material.AIR || currentItem.amount <= 0) {
                    // place one item from cursor into slot
                    event.isCancelled = true
                    event.currentItem = cursor.clone().apply {
                        amount = 1
                    }
                    event.cursor.amount = max(0, cursor.amount - 1)
                    return
                }

                if (currentItem.isSimilar(cursor)) {
                    // add one item from cursor to item in slot
                    val curAmount = currentItem.amount
                    if (curAmount + 1 <= currentItem.maxStackSize) {
                        event.currentItem!!.amount += 1
                        event.cursor.amount = max(0, cursor.amount - 1)
                    }
                    event.isCancelled = true
                    return
                }

                // swap cursor and item in slot
                val copyCurrent = event.currentItem?.clone()
                event.currentItem = cursor.clone()
                event.view.setCursor(copyCurrent)
                event.isCancelled = true
            }

            return
        }

        // Quick move items from bottom inv
        if (event.isShiftClick) {
            val moving = currentItem ?: return
            if (moving.type == Material.AIR) {
                return
            }
            event.isCancelled = true
            // `addItem` treats the whole top inventory as fair game, including the RESULT slot (2),
            // which let a player shift-click arbitrary items straight into the output. Fill only
            // the two input slots.
            val remaining = moving.clone()
            for (slot in 0..1) {
                if (remaining.amount <= 0) {
                    break
                }
                val existing = topInventory.getItem(slot)
                if (existing == null || existing.type == Material.AIR) {
                    topInventory.setItem(slot, remaining.clone())
                    remaining.amount = 0
                } else if (existing.isSimilar(remaining)) {
                    val transfer = min(remaining.amount, existing.maxStackSize - existing.amount)
                    if (transfer > 0) {
                        existing.amount += transfer
                        topInventory.setItem(slot, existing)
                        remaining.amount -= transfer
                    }
                }
            }
            event.currentItem = if (remaining.amount <= 0) null else remaining
            return
        }

    }

    private fun getGrindingSeed(bukkitPlayer: Player): Long {
        var seed = bukkitPlayer.persistentDataContainer.get(
            RecipeSeeds.playerGrindingSeedKey,
            PersistentDataType.LONG
        )
        if (seed == null) {
            seed = Random.nextLong()
            bukkitPlayer.persistentDataContainer.set(
                RecipeSeeds.playerGrindingSeedKey,
                PersistentDataType.LONG,
                seed
            )
        }
        return seed
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory is GrindstoneInventory) {
            recipeCache.invalidate(event.player.uniqueId)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        recipeCache.invalidate(event.player.uniqueId)
    }

}