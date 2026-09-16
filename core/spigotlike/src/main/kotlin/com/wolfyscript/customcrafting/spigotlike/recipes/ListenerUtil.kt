package com.wolfyscript.customcrafting.spigotlike.recipes

import com.wolfyscript.customcrafting.core.CustomCrafting
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager

/**
 * Registers the workstation listeners that are enabled in `config/recipes/workstations.conf`.
 *
 * A disabled workstation is never registered at all, so it costs nothing at runtime — which matters
 * because six of these hook `InventoryClickEvent` (every inventory click on the server) and two hook
 * `PlayerInteractEvent` (every right click).
 */
fun PluginManager.registerCommonRecipeListeners(plugin: Plugin, customCrafting: CustomCrafting) {
    val settings = customCrafting.configurationManager.workstationSettings

    fun register(enabled: Boolean, listener: () -> Listener) {
        if (enabled) {
            registerEvents(listener(), plugin)
        }
    }

    register(settings.anvil) { AnvilListener(plugin, customCrafting) }
    register(settings.campfire) { CampfireListener(customCrafting) }
    register(settings.cauldron) { CauldronListener(customCrafting) }
    register(settings.crafter) { CrafterListener(plugin, customCrafting) }
    register(settings.craftingTable) { CraftingListener(plugin, customCrafting) }
    register(settings.furnace) { FurnaceListener(customCrafting) }
    register(settings.grindstone) { GrindstoneListener(customCrafting) }
    register(settings.smithingTable) { SmithingListener(plugin, customCrafting) }
}
