package com.wolfyscript.customcrafting.core.configuration.recipes

import com.fasterxml.jackson.annotation.JsonCreator

class WorkstationSettingsImpl @JsonCreator constructor(
    override val craftingTable: Boolean = true,
    override val crafter: Boolean = true,
    override val furnace: Boolean = true,
    override val campfire: Boolean = true,
    override val anvil: Boolean = true,
    override val smithingTable: Boolean = true,
    override val grindstone: Boolean = true,
    override val stonecutter: Boolean = true,
    override val cauldron: Boolean = true,
) : WorkstationSettings {

    /** Lists only what is switched OFF, so the startup log stays quiet on a default setup. */
    fun disabledNames(): List<String> = buildList {
        if (!craftingTable) add("craftingTable")
        if (!crafter) add("crafter")
        if (!furnace) add("furnace")
        if (!campfire) add("campfire")
        if (!anvil) add("anvil")
        if (!smithingTable) add("smithingTable")
        if (!grindstone) add("grindstone")
        if (!stonecutter) add("stonecutter")
        if (!cauldron) add("cauldron")
    }

    override fun toString(): String {
        val disabled = disabledNames()
        return if (disabled.isEmpty()) "WorkstationSettings (all enabled)"
        else "WorkstationSettings (disabled: ${disabled.joinToString(", ")})"
    }
}
