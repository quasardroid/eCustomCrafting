package com.wolfyscript.customcrafting.core.configuration.recipes

/**
 * Which workstation listeners CustomCrafting registers with the server.
 *
 * There is one switch per listener. Turning one off means the corresponding recipe type simply does
 * nothing — the recipes still load and still show up in `/recipes status`, but no event is handled
 * for that workstation, so nothing is evaluated and nothing is consumed.
 *
 * Everything defaults to `true`, i.e. the behaviour CustomCrafting has always had.
 *
 * Why this is worth touching at all: six of these listeners hook [InventoryClickEvent], which fires
 * for **every inventory click on the server**, and two hook [PlayerInteractEvent], which fires for
 * **every right click**. Each one filters out quickly, but if you never use a workstation there is
 * no reason to pay for its handler at all.
 */
interface WorkstationSettings {

    /** Crafting table recipes (`crafting`). Hooks InventoryClickEvent + PrepareItemCraftEvent. */
    val craftingTable: Boolean

    /** The 1.21 Crafter block, also `crafting`. Hooks CrafterCraftEvent + InventoryClickEvent. */
    val crafter: Boolean

    /** Furnace / blast furnace / smoker (`cooking`). Hooks the Furnace events + BlockExpEvent. */
    val furnace: Boolean

    /** Campfire cooking (`cooking`). Hooks CampfireStartEvent, PlayerInteractEvent, BlockCookEvent. */
    val campfire: Boolean

    /** Anvil recipes (`repairing`). Hooks PrepareAnvilEvent + InventoryClickEvent. */
    val anvil: Boolean

    /** Smithing table recipes (`smithing`). Hooks PrepareSmithingEvent + SmithItemEvent. */
    val smithingTable: Boolean

    /** Grindstone recipes (`grinding`). Hooks PrepareGrindstoneEvent + two InventoryClickEvents. */
    val grindstone: Boolean

    /**
     * Stonecutter recipes (`stonecutting`). Hooks PlayerStonecutterRecipeSelectEvent.
     *
     * Paper only — this listener does not exist on plain Spigot, so the setting is ignored there.
     */
    val stonecutter: Boolean

    /**
     * Cauldron recipes (`mixing`). Hooks PlayerInteractEvent.
     *
     * The cauldron feature is **not implemented yet**: the handler ends in a `// TODO: cauldron GUI`
     * and does nothing. It is left on by default for consistency, but turning it off costs you
     * nothing today and removes one handler from every right click.
     */
    val cauldron: Boolean

}
