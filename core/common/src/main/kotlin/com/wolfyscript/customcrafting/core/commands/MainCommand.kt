package com.wolfyscript.customcrafting.core.commands

import com.mojang.brigadier.CommandDispatcher
import com.wolfyscript.customcrafting.CustomCraftingProvider
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.identifier.Key
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions

object MainCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        sequenceOf("cc", Key.CUSTOMCRAFTING_NAMESPACE).forEach {
            dispatcher.register(Commands.literal(it)
                // Without this the node defaults to permission level 0, so any player could run
                // `/cc backup` and drive a full recursive ZIP of the data directory on the main
                // thread as fast as they could click.
                .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                .then(Commands.literal("info").executes {

                    CustomCraftingProvider.get().logger.info("Ran CustomCrafting info")
                    // TODO: Display version etc.

                    return@executes SUCCESS_RESULT
                })
                // `/cc reload` is the spelling people reach for; it runs the same reload as
                // `/recipes reload`.
                .then(Commands.literal("reload").executes { ctx ->
                    RecipesCommand.reload(ctx, CustomCraftingProvider.get())
                })
                .then(Commands.literal("backup").executes { ctx ->
                    val customCrafting = CustomCraftingProvider.get()
                    customCrafting.logger.info("Create backup")
                    ctx.source.sendSuccess({ Component.literal("Creating backup...") }, false)

                    // Zipping the whole resource tree is blocking IO; doing it inline froze the
                    // server tick loop for as long as the backup took.
                    ScafallProvider.get().scheduler.async(customCrafting) {
                        try {
                            customCrafting.server!!.resourceManager.backupManager.createBackup()
                            ctx.source.sendSuccess({ Component.literal("Backup created.") }, false)
                        } catch (ex: Exception) {
                            customCrafting.logger.error("Failed to create backup", ex)
                            ctx.source.sendFailure(
                                Component.literal("Backup failed: ${ex.message ?: "Unknown error"}. See the server log.")
                            )
                        }
                    }

                    return@executes SUCCESS_RESULT
                })
            )
        }
    }

}