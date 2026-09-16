package com.wolfyscript.customcrafting.core.recipe.ingredient

import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.recipe.RemainsIgnoreOptions
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.scafall.items.ItemStackRef
import com.wolfyscript.scafall.wrappers.minecraft.wrap
import com.wolfyscript.scafall.wrappers.world.items.ItemStackSnapshot
import com.wolfyscript.scafall.wrappers.world.items.ScafallItemStack

internal class IngredientRemainderCustomImpl(
    override val ignore: RemainsIgnoreOptions = RemainsIgnoreOptionsImpl(vanilla = false, others = false),
    override val remainder: ItemStackRef,
) : IngredientRemainder.Custom {

    override fun calculate(
        target: ItemStackSnapshot,
        count: Int,
        ref: ItemStackRef,
        context: EvaluationContext,
        evalResult: RecipeEvaluationResult<*, *>,
    ): List<ScafallItemStack> {
        val mcSource = target.unwrap()
        val vanillaRemainder = mcSource.item.craftingRemainder

        if (!ignore.others) {
            // TODO: determine remains from third-party mods/plugins
        }

        // The configured remainder is the point of this class, so it always wins. The vanilla
        // remainder is only added alongside it when it is not being ignored — previously the vanilla
        // one short-circuited and the configured one was thrown away for every item that has one
        // (buckets, bottles...), which is exactly when someone configures a custom remainder.
        return listOfNotNull(
            remainder.create(),
            vanillaRemainder?.takeIf { !ignore.vanilla }?.create()?.wrap(),
        )
    }

    override fun toString(): String {
        return "(ignore=$ignore, remainder=$remainder)"
    }

}