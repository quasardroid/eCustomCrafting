package com.wolfyscript.customcrafting.editor.cli

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.core.commands.SUCCESS_RESULT
import com.wolfyscript.customcrafting.editor.EditorRegistryTypes
import com.wolfyscript.customcrafting.editor.domain.SessionModel
import com.wolfyscript.customcrafting.editor.recipeEditor
import com.wolfyscript.customcrafting.core.registry.CustomCraftingRegistryTypes
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.customcrafting.core.util.customCrafting
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.identifier.toKey
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component

internal fun LiteralArgumentBuilder<CommandSourceStack>.recipeEditorCLIEntry() {
    then(
        Commands.literal("editor")
            .then(Commands.literal("save").then(
                Commands.argument("recipe_name", StringArgumentType.word()).executes { ctx ->
                    val executor = ctx.source.player ?: return@executes 0

                    val editor = CustomCraftingProvider.get().server?.recipeEditor
                        ?: return@executes SUCCESS_RESULT
                    val session = editor.getOrCreateSession(executor.uuid).getOrThrow()
                    val model = session.model ?: return@executes SUCCESS_RESULT

                    val recipeName = StringArgumentType.getString(ctx, "recipe_name")

                    // Report what actually happened; this used to claim success even when the save
                    // failed, with the real error only in the server log.
                    val saveResult = when (model) {
                        is SessionModel.CreateModel -> model.save(Key.customCrafting(recipeName))
                        is SessionModel.EditModel -> model.saveAs(Key.customCrafting(recipeName))
                        else -> Result.failure(IllegalStateException("Nothing is being edited"))
                    }

                    saveResult.onFailure {
                        ctx.source.sendFailure(
                            Component.literal("Failed to save recipe $recipeName: ${it.message ?: "Unknown error"}")
                        )
                        return@executes 0
                    }

                    // Release the session, otherwise `create` refuses forever and the player can
                    // only ever make one recipe per server uptime.
                    session.cancel()
                    ctx.source.sendSuccess({ Component.literal("Saved recipe under $recipeName") }, false)
                    return@executes SUCCESS_RESULT
                }
            ))
    )
    then(
        Commands.literal("create")
            .then(Commands.argument("type", IdentifierArgument.id()).executes { ctx ->
                val executor = ctx.source.player ?: return@executes 0

                val editor = CustomCraftingProvider.get().server?.recipeEditor
                    ?: return@executes SUCCESS_RESULT
                val session = editor.getOrCreateSession(executor.uuid).getOrThrow()

                val typeId = IdentifierArgument.getId(ctx, "type").toKey()
                val recipeType = CustomCraftingRegistryTypes.recipeTypes.resolveOrThrow()[typeId]
                    ?: return@executes SUCCESS_RESULT

                val createResult = session.create(recipeType)

                if (createResult.isSuccess) {
                    ctx.source.sendSuccess({ Component.literal("You are now editing a new $recipeType recipe") }, false)
                    return@executes SUCCESS_RESULT
                }

                ctx.source.sendFailure(Component.literal("Failed to create $recipeType recipe: ${createResult.exceptionOrNull()?.message ?: "Unknown error"}"))
                return@executes 0
            }.suggests { context, builder ->
                // Only offer recipe types that actually have an editor factory registered.
                // Suggesting every registered recipe type sent players straight into
                // "missing type factory for recipe type ..." for six of the seven.
                val editable = EditorRegistryTypes.recipeTypeSpecificModelFactories.resolveOrThrow()
                    .values().mapTo(HashSet()) { it.recipeType }
                for (key in CustomCraftingRegistryTypes.recipeTypes.resolveOrThrow().keySet()) {
                    val recipeType = CustomCraftingRegistryTypes.recipeTypes.resolveOrThrow()[key]
                    if (recipeType == null || recipeType !in editable) {
                        continue
                    }
                    if (key.namespace == Key.CUSTOMCRAFTING_NAMESPACE) {
                        builder.suggest(key.value)
                    } else {
                        builder.suggest(key.toString())
                    }
                }
                return@suggests builder.buildFuture()
            })
    )
    then(
        Commands.literal("cancel").executes { ctx ->
            val executor = ctx.source.player ?: return@executes 0
            val editor = CustomCraftingProvider.get().server?.recipeEditor
                ?: return@executes SUCCESS_RESULT
            val session = editor.getOrCreateSession(executor.uuid).getOrThrow()
            if (session.model == null) {
                ctx.source.sendFailure(Component.literal("You are not editing a recipe"))
                return@executes 0
            }
            // Without a way to release the session, `create` refused for the rest of the uptime.
            session.cancel()
            ctx.source.sendSuccess({ Component.literal("Cancelled the recipe editor session") }, false)
            return@executes SUCCESS_RESULT
        }
    )
    then(
        Commands.literal("edit")
            .then(Commands.argument("recipe", IdentifierArgument.id()).executes { ctx ->
                // The body used to be empty while still reporting success.
                val executor = ctx.source.player ?: return@executes 0
                val editor = CustomCraftingProvider.get().server?.recipeEditor
                    ?: return@executes SUCCESS_RESULT
                val session = editor.getOrCreateSession(executor.uuid).getOrThrow()

                val recipeKey = IdentifierArgument.getId(ctx, "recipe").toKey()
                val editResult = session.edit(recipeKey)

                editResult.onFailure {
                    ctx.source.sendFailure(
                        Component.literal("Failed to edit $recipeKey: ${it.message ?: "Unknown error"}")
                    )
                    return@executes 0
                }
                ctx.source.sendSuccess({ Component.literal("You are now editing $recipeKey") }, false)
                return@executes SUCCESS_RESULT
            }.suggests { context, builder ->
                CustomCraftingProvider.get().server?.recipeManager?.let { manager ->
                    for (reference in manager.recipes()) {
                        builder.suggest(reference.toString())
                    }
                }
                return@suggests builder.buildFuture()
            })
    )
}