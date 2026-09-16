package com.wolfyscript.customcrafting.core.configuration.recipes

/**
 * Controls how much of CustomCrafting is exposed to the *vanilla* recipe system.
 *
 * CustomCrafting registers a "placeholder" recipe with the server for most custom recipes. Those
 * placeholders are NOT cosmetic — for some workstations vanilla has to match a recipe before
 * CustomCrafting ever receives an event — so each switch here says exactly what it costs.
 */
interface RecipeBookSettings {

    /**
     * Whether the recipe book is pushed to players whenever the recipe set changes.
     *
     * Turning this off is the cheap win if nobody uses the vanilla recipe book: the server keeps
     * registering and matching recipes exactly as before, it simply never sends the recipe-book
     * update packet. That packet carries the WHOLE book to EVERY online player, and it is by far
     * the most expensive part of a startup or a `/cc reload`.
     *
     * Cost of disabling: players' recipe books will not list custom recipes and will not refresh
     * after a reload until they rejoin. Crafting itself is unaffected.
     */
    val syncToPlayers: Boolean

    /**
     * Whether the placeholder recipes are registered with the server at all.
     *
     * **Only turn this off if you understand the consequences.** Disabling it skips all platform
     * recipe registration, which removes the registration cost entirely, but:
     *
     * - **Custom cooking recipes (furnace, blast furnace, smoker) stop working.** Vanilla decides
     *   whether an input is smeltable at all; with no placeholder it never starts the burn, so
     *   CustomCrafting never gets `FurnaceStartSmeltEvent`.
     * - **Custom stonecutting recipes stop working.** The stonecutter's option list is vanilla's,
     *   and CustomCrafting resolves the selected option back through the placeholder key.
     * - Crafting-table and smithing recipes keep working (CustomCrafting drives those from the
     *   `Prepare…Event` itself), but clients may flicker, because the client no longer predicts a
     *   result for the grid it is looking at.
     *
     * If you only want to get rid of the recipe book, use [syncToPlayers] instead.
     */
    val registerPlaceholders: Boolean

}
