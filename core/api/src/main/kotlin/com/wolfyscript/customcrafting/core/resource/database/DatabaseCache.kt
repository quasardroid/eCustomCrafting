package com.wolfyscript.customcrafting.core.resource.database

import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache databases that are already used, so destinations using the same database use just one connection.
 *
 * Saves are issued from command/editor threads while loading runs on the main thread, so this global
 * map is genuinely shared across threads and must not be a plain [HashMap].
 */
internal object DatabaseCache {

    val connections: MutableMap<DatabaseInfo, Database> = ConcurrentHashMap()

}