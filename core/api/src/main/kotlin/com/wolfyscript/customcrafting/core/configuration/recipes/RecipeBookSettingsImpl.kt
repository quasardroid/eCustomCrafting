package com.wolfyscript.customcrafting.core.configuration.recipes

import com.fasterxml.jackson.annotation.JsonCreator

class RecipeBookSettingsImpl @JsonCreator constructor(
    override val syncToPlayers: Boolean = true,
    override val registerPlaceholders: Boolean = true,
) : RecipeBookSettings {

    override fun toString(): String {
        return "RecipeBookSettings (syncToPlayers=$syncToPlayers, registerPlaceholders=$registerPlaceholders)"
    }
}
