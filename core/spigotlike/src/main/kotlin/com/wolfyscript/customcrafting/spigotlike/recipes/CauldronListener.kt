package com.wolfyscript.customcrafting.spigotlike.recipes

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.mechanics.CauldronSettings
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class CauldronListener(val customCrafting: CustomCrafting) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (event.item != null && event.item!!.type != Material.AIR) {
            return // Only allow interactions with empty hand
        }
        val block = event.clickedBlock ?: return
        if (block.type != Material.CAULDRON && block.type != Material.WATER_CAULDRON && block.type != Material.LAVA_CAULDRON) {
            return
        }

        // `gameMechanicSettings` is still `TODO("Not yet implemented")`, so reading it throws
        // NotImplementedError — out of an event handler, on every cauldron right-click. Fall back to
        // the default interaction until the mechanics config actually exists.
        val interactionType = runCatching {
            customCrafting.configurationManager.gameMechanicSettings.cauldron.interactionType
        }.getOrDefault(CauldronSettings.InteractionType.DEFAULT)

        val allowedInteraction = when (interactionType) {
            CauldronSettings.InteractionType.SNEAKING -> {
                event.player.isSneaking
            }
            CauldronSettings.InteractionType.DEFAULT -> {
                !event.player.isSneaking
            }
        }
        if (!allowedInteraction) {
            return
        }

        // TODO: cauldron GUI
    }

}