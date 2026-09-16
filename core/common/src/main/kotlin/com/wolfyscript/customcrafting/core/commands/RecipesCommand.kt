package com.wolfyscript.customcrafting.core.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.adventure.deser
import com.wolfyscript.scafall.adventure.vanilla
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.identifier.toScafall
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions
import java.util.concurrent.atomic.AtomicBoolean

object RecipesCommand {

    const val ROOT_NAME = "recipes"

    /** Guards against two overlapping reloads racing over the recipe index. */
    private val reloading = AtomicBoolean(false)

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        sequenceOf(ROOT_NAME, "cc:$ROOT_NAME", "${Key.CUSTOMCRAFTING_NAMESPACE}:$ROOT_NAME").forEach { alias ->
            dispatcher.register(
                Commands.literal(alias).requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }.apply {
                    then(Commands.literal("reload").executes { ctx -> reload(ctx, CustomCraftingProvider.get()) })
                    then(Commands.literal("status").executes { ctx ->
                        printStatus(ctx, CustomCraftingProvider.get())
                        return@executes SUCCESS_RESULT
                    })
                    then(
                        Commands.literal("disable")
                            .then(Commands.argument("recipe", IdentifierArgument.id()).executes { ctx ->
                                val recipeKey = IdentifierArgument.getId(ctx, "recipe").toScafall()
                                val recipeManager = CustomCraftingProvider.get().server!!.recipeManager
                                // disableRecipe silently does nothing for an unknown key, so a typo
                                // told the operator an exploitable recipe was off while it was not.
                                if (recipeManager.getRecipe(recipeKey) == null) {
                                    ctx.source.sendFailure(Component.literal("Unknown recipe $recipeKey"))
                                    return@executes SUCCESS_RESULT
                                }
                                recipeManager.disableRecipe(recipeKey)

                                ctx.source.sendSuccess({ Component.literal("Disabled Recipe $recipeKey") }, false)
                                return@executes SUCCESS_RESULT
                            }.suggests { ctx, builder ->
                                // Iterate directly and match case-insensitively. The map/filter
                                // chain built two throwaway lists of every recipe key on EVERY
                                // keystroke, and a lowercase prefix matched nothing.
                                val prefix = builder.remainingLowerCase
                                for (key in CustomCraftingProvider.get().server!!.recipeManager.recipesLoadedByCC) {
                                    val rendered = key.toString()
                                    if (rendered.lowercase().startsWith(prefix)) {
                                        builder.suggest(rendered)
                                    }
                                }

                                return@suggests builder.buildFuture()
                            })
                    )
                    then(
                        Commands.literal("enable")
                            .then(Commands.argument("recipe", IdentifierArgument.id()).executes { ctx ->
                                val recipeKey = IdentifierArgument.getId(ctx, "recipe").toScafall()
                                val recipeManager = CustomCraftingProvider.get().server!!.recipeManager
                                if (!recipeManager.isRecipeDisabled(recipeKey)) {
                                    ctx.source.sendFailure(Component.literal("Recipe $recipeKey is not disabled"))
                                    return@executes SUCCESS_RESULT
                                }
                                recipeManager.enableRecipe(recipeKey)

                                ctx.source.sendSuccess({ Component.literal("Enabled Recipe $recipeKey") }, false)
                                return@executes SUCCESS_RESULT
                            }.suggests { ctx, builder ->
                                val prefix = builder.remainingLowerCase
                                for (key in CustomCraftingProvider.get().server!!.recipeManager.disabledRecipes) {
                                    val rendered = key.toString()
                                    if (rendered.lowercase().startsWith(prefix)) {
                                        builder.suggest(rendered)
                                    }
                                }

                                return@suggests builder.buildFuture()
                            })
                    )
                }
            )
        }
    }

    internal fun reload(ctx: CommandContext<CommandSourceStack>, customCrafting: CustomCrafting): Int {
        if (!reloading.compareAndSet(false, true)) {
            ctx.source.sendFailure(Component.literal("A reload is already running."))
            return SUCCESS_RESULT
        }
        ctx.source.sendSuccess({ Component.literal("Reloading CustomCrafting resources...") }, false)
        ScafallProvider.get().scheduler.async(customCrafting) {
            // PARSE PHASE — off the main thread.
            // Reading every recipe file, deserialising it and rebuilding the immutable recipe index
            // is by far the most expensive part of a reload, and none of it touches server state.
            //
            // The reload used to report success before it had run, so a failure only ever showed up
            // in the server log.
            try {
                val startedAt = System.nanoTime()
                // The runtime reload path, not the first-startup one: `loadResources` re-exports the
                // shipped defaults and re-runs onPrepare every time.
                customCrafting.server!!.resourceManager.resourceLoader.reloadResources()
                val parseMillis = (System.nanoTime() - startedAt) / 1_000_000

                // APPLY PHASE — the platform decides how to get onto the main thread, and spreads
                // the registry work over several ticks so the tick loop never stalls.
                customCrafting.server!!.onRecipesReloaded()

                ctx.source.sendSuccess({
                    Component.literal("Reloaded CustomCrafting resources in ${parseMillis}ms. Syncing recipes to the server...")
                }, false)
            } catch (ex: Exception) {
                customCrafting.logger.error("Failed to reload resources", ex)
                ctx.source.sendFailure(Component.literal("Reload failed: ${ex.message}. See the server log."))
            } finally {
                reloading.set(false)
            }
        }
        return SUCCESS_RESULT
    }

    private fun printStatus(ctx: CommandContext<CommandSourceStack>, customCrafting: CustomCrafting) {
        val recipeManager = customCrafting.server!!.recipeManager

        val totalRecipeCount = recipeManager.recipes().count()
        val ccRecipesCount = recipeManager.recipesLoadedByCC.size
        val thirdPartyRecipeCount = totalRecipeCount - ccRecipesCount
        val disabledRecipeCount = recipeManager.disabledRecipes.size
        val failedCount = recipeManager.invalidRecipes.size

        val message = """
            <green>Loaded Recipes: <b>$totalRecipeCount</b>
              CustomCrafting: $ccRecipesCount
              3rd-Parties: $thirdPartyRecipeCount
            </green>    
            <red>Failed to load: <b>$failedCount</b></red>
            
            <gray>Disabled Recipes: <b>$disabledRecipeCount</b></gray>
            """.trimIndent()

        ctx.source.sendSuccess({
            message.deser().vanilla()
        }, false)
    }

}