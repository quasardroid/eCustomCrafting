package com.wolfyscript.customcrafting.editor.domain.model.recipe

import com.wolfyscript.customcrafting.editor.EditorRegistryTypes
import com.wolfyscript.customcrafting.core.recipe.CustomRecipe
import com.wolfyscript.customcrafting.core.recipe.CustomRecipeCrafting
import com.wolfyscript.customcrafting.core.util.customCrafting
import com.wolfyscript.customcrafting.editor.EditorModule
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.registry.ValueReference
import com.wolfyscript.scafall.registry.referenced

object RecipeTypeSpecificStateFactories {

    val crafting = create<CustomRecipeCrafting>("crafting")

    // `cooking`, `mixing`, `repairing`, `smithing`, `stonecutting` and `grinding` used to be declared
    // here as well — all six copy-pasted as `create<CustomRecipeCrafting>`, so they claimed the wrong
    // recipe type, none of them had a factory registered in EditorRegistries, and nothing referenced
    // them. Declare each one back alongside its real factory, with its own type parameter.

    private inline fun <reified T : CustomRecipe<*, *>> create(key: String): ValueReference<RecipeModel.RecipeTypeSpecificModel.Factory<*>, RecipeModel.RecipeTypeSpecificModel.Factory<T>> {
        return EditorRegistryTypes.recipeTypeSpecificModelFactories.key
            .referenced<RecipeModel.RecipeTypeSpecificModel.Factory<*>, RecipeModel.RecipeTypeSpecificModel.Factory<T>>(
                Key.customCrafting(key)
            ).reference { EditorModule.get().registries }
    }

}