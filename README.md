<div align="center">
  <img src="https://i.imgur.com/wLHwJ1c.png" alt="CustomCrafting" />
</div>

## Changes in this fork

This fork carries a full read-only audit of the v5 codebase and the remediation of its findings.
**113 of the 115 findings are fixed.** The audit report, with every finding, its `file:line`, a
concrete failure scenario and the remediation status, is in
[`AUDITORIA_ECUSTOMCRAFTING.md`](AUDITORIA_ECUSTOMCRAFTING.md).

> [!warning]
> Everything below is **compile-verified only** (`compileKotlin` + `compileJava`, Gradle 9.5.1,
> JDK 25) for every module except `ui-common`, which cannot be built at all because
> `com.wolfyscript.viewportl:viewportl-api` is not published in any declared repository.
> **None of it has been tested on a running server.**

### Item duplication and destruction

All six Critical findings were item dupe or item loss, every one reachable by an unprivileged player
at a vanilla workstation.

* **Anvil and smithing table handed out the result twice.** A shift-click added it to the inventory
  and then fell through to the cursor branch as well. The "cursor holds a different item" case was
  worse: it charged the levels, consumed the ingredients and destroyed the result.
* **Grindstone merged a foreign result onto the cursor** (the `isSimilar` test was inverted) and
  consumed the ingredients even when nothing was collected. Placing an ingredient wiped the rest of
  the cursor stack, and shift-clicking could shove arbitrary items into the output slot.
* **The stonecutter handler fired on the player's own inventory**, because it tested the view's top
  inventory instead of the clicked one. Every ordinary left-click pickup was also cancelled.
* **The trimmed crafting matrix was indexed wrongly** — no row-stride multiplication, and
  `maxColumn` used where `minColumn` belongs. Every recipe smaller than the grid read the wrong
  slots and wrote the shrunk stacks back to the wrong ones.
* **Smithing matched anything.** The ingredient match results were never null-checked, so any three
  non-empty items produced the recipe's output and consumed nothing.

### Recipes that loaded but never worked

* **The registration was silently dropped.** `verifyRecipesAndLoad` reached its own manager through
  `customCrafting.server?.recipeManager?`, but the manager is constructed *inside* the
  `CustomCraftingServer` constructor, so that gate could still be null — swallowing the whole
  registration after the keys had already been counted as loaded. Recipes then showed up in
  `/recipes status`, never matched when crafting, and could not be opened in the editor.
* **Registering one recipe unregistered every other**, because the lookup maps were rebuilt from
  only the incoming batch.
* **Recipe priority was inverted**, so the documented way to override a recipe never fired.
* **Shapeless matching pruned valid assignments**, making a recipe craftable or not depending on
  which slots the player used.
* **One extension-less file in `recipes/` aborted the whole directory walk**, so a stray `README`
  stopped every recipe from loading.

### Performance

* **`/cc reload` no longer freezes the server.** Parsing runs off the main thread; the Bukkit
  registry update is applied on the main thread in 200-recipe slices.
* **The recipe book is resynced once per batch, not twice per recipe.** `addRecipe`/`removeRecipe`
  now pass `resendRecipes = false`, followed by a single `Bukkit.updateRecipes()`. The
  single-argument overloads push the entire recipe book to every online player on each call.
* A reload now uses a real reload path (`onReload`) instead of re-running the first-startup path,
  which re-exported the shipped default recipes from the jar on every invocation.
* Per-recipe `info` logging demoted to `debug` — it was thousands of synchronised appender writes
  contending with the main thread.
* Unbounded listener caches bounded, the full server-wide recipe scan on every `PrepareSmithingEvent`
  short-circuited, backup moved off the main thread, and assorted dead code removed.

### New configuration

Defaults preserve the existing behaviour in every case.

* **`config/recipes/workstations.conf`** — one switch per workstation listener (crafting table,
  crafter, furnace, campfire, anvil, smithing table, grindstone, stonecutter, cauldron). Six of
  these listeners hook `InventoryClickEvent` and two hook `PlayerInteractEvent`, so a workstation you
  never use is pure overhead. Disabling one also skips its placeholder registration.
* **`config/recipes/recipe_book.conf`** — `syncToPlayers` (stop pushing the recipe book to players
  while keeping every recipe working) and `registerPlaceholders`. Each option documents exactly what
  it costs; `registerPlaceholders = false` **breaks custom cooking and stonecutting**, because
  vanilla has to match first for those.
* `/cc reload` was added as an alias of `/recipes reload`.

### Not applied

Two findings were left alone on purpose, both documented in the report: `BUILD-7`
(`modImplementation` is not a configuration in this project, so the suggested fix does not apply)
and `FABRIC-5` (moving the mixin to the apply phase cannot be verified here, and a wrong injection
target breaks the whole mod; the thread-safety concern behind it was closed another way).

---

> [!note]  
> v5 is in early alpha and may not work properly yet.
> Breaking API changes may be introduced at anytime without prior warning!
> 
> ## Breaking Changes & Planned New Features
> * Removed Elite Crafting Table
> * Removed CustomItem; Focus is instead on integrating items from other plugins/mods
> * Removed Advanced Crafting Table
> * New Recipe structure: more composable recipes
> * New Resource Loader for multiple SQL and custom local destinations
> * Editor API: create and edit recipes via CLI or GUI
> * Cross-Platform: Spigot, Paper, Sponge, Fabric, and more in the future
> * Modded: Makes use of Minecraft internals to better support custom recipes
> * ...
> 

# CustomCrafting

![bStats Servers](https://img.shields.io/bstats/servers/3211)
![Spiget Downloads](https://img.shields.io/spiget/downloads/55883)
![Spiget Stars](https://img.shields.io/spiget/stars/55883)

CustomCrafting allows you to create custom recipes for a vast variety of
workstations including:  
Crafting Table, Furnace, Blast Furnace, Smoker, Smithing, and more.

Additionally, you can toggle vanilla recipes as you like, and disable and override them.

It integrates with other plugins like Oraxen, ItemsAdder, MMOItems, and MythicMobs to support
your custom items and update recipes automatically when they are changed.

Before creating an issue, please go to the wiki. It should clear up frequently asked questions.

- [**Wiki**](https://github.com/WolfyScript/CustomCrafting/wiki)
- [**JavaDocs**](https://wolfyscript.github.io/CustomCrafting-Wiki/)

For any questions join the [Discord](https://discord.gg/qGhDTSr).

![recipe_types](https://github.com/WolfyScript/CustomCrafting/assets/41468455/f36d3d23-c094-4a47-9b45-370f2f314d21)
![customization](https://github.com/WolfyScript/CustomCrafting/assets/41468455/8415f2e3-18cc-49e5-99ae-c910f4c97c56)
![advanced_settings](https://github.com/WolfyScript/CustomCrafting/assets/41468455/8d74313c-e5e8-4171-a120-a0b0bd9e2d74)
![dependencies_updates](https://github.com/WolfyScript/CustomCrafting/assets/41468455/0a80e1ff-0c07-419d-bd88-7172aabe5096)

[![](https://bstats.org/signatures/bukkit/CustomCrafting.svg)](https://bstats.org/plugin/bukkit/CustomCrafting/3211)

## Config & Resource Directory Structure

* `<root>` - actual location depends on the platform. (on spigot `plugins/customcrafting`)
  * `.data` - data that is cached and used internally by CC
  * `config` - CC configuration
  * `resources` - default location for resources
    * `defaults` - the default resources shipped with CC
    * `<dir>` - any custom directory from which to load resources
      * `recipes` - recipe config files
      * `ingredients` - ingredient config files

## Check out my partner!
<a href="https://billing.kinetichosting.net/aff.php?aff=345">
  <img width="700px" src="https://user-images.githubusercontent.com/41468455/237019976-6b66b7f4-3d26-4b2f-b858-463ffe675531.png" alt="Kinetic Hosting 15% off your first month with code WOLFYSCRIPT"/>
</a>



