package com.wolfyscript.customcrafting.paper

import com.wolfyscript.customcrafting.paper.recipes.StonecutterListener
import com.wolfyscript.customcrafting.core.server.CustomCraftingServer
import com.wolfyscript.customcrafting.spigotlike.CustomCraftingServerSpigotLike
import org.bukkit.Bukkit

class CustomCraftingServerPaper(val spigotLike: CustomCraftingServerSpigotLike) :
    CustomCraftingServer by spigotLike {

    override fun onLoad() {
        spigotLike.onLoad()

        // Paper-only listener; `workstations.conf: stonecutter` is ignored on plain Spigot because
        // this is never reached there.
        if (spigotLike.customCrafting.configurationManager.workstationSettings.stonecutter) {
            Bukkit.getPluginManager().apply {
                registerEvents(StonecutterListener(spigotLike.customCrafting), spigotLike.plugin)
            }
        }
    }

}