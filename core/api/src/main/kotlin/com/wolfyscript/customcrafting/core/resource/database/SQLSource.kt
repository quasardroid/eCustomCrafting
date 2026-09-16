package com.wolfyscript.customcrafting.core.resource.database

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.resources.SourceSettings
import com.wolfyscript.customcrafting.core.resource.DataType
import com.wolfyscript.customcrafting.core.resource.Source
import com.wolfyscript.customcrafting.core.resource.DestinationFilter
import com.wolfyscript.customcrafting.core.resource.LoadedObject
import com.wolfyscript.customcrafting.core.resource.ResourceLoaderImpl
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.scafall.identifier.Key
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal class SQLSource(customCrafting: CustomCrafting, override val settings: SourceSettings.SQLSourceSettings) : Source {

    override val filter: Source.Filter? =
        settings.filter?.let { DestinationFilter(customCrafting, it) }

    private fun getOrCreateDBConnection(): Database {
        val connector = settings.connection
        val databaseInfo = DatabaseInfo(connector.jdbcUrl, connector.driver, connector.user)

        // Atomic: the previous check-then-put could open (and leak) a second pool for the same
        // database when two sources initialised concurrently.
        return DatabaseCache.connections.computeIfAbsent(databaseInfo) {
            Database.connect(connector.jdbcUrl, connector.driver, user = connector.user, password = connector.password)
        }
    }

    override fun <T : Any> load(type: DataType<T>, accept: (value: LoadedObject<T>) -> Unit) {
        DataTables.getTable(type)?.let { table ->
            transaction(getOrCreateDBConnection()) {
                SchemaUtils.create(table)

                table.selectAll().forEach {
                    var dir = it[table.dir]
                    var key = it[table.name]
                    if (dir.endsWith('/')) {
                        dir = dir.dropLast(1)
                    }
                    if (key.startsWith('/')) {
                        key = key.substring(1)
                    }

                    // A root-level resource is stored with an empty dir; joining unconditionally
                    // would produce the leading-slash key "/name", which never matches what was saved.
                    val recipeKey = Key.key(
                        Key.CUSTOMCRAFTING_NAMESPACE,
                        if (dir.isEmpty()) key else "$dir/$key"
                    )
                    val recipe = it[table.config]
                    val loadedRecipe = ResourceLoaderImpl.LoadedObjectImpl(recipeKey, recipe)
                    accept(loadedRecipe)
                }
            }
        }
    }

    override fun <T: Any> save(
        type: DataType<T>,
        key: Key,
        value: T,
    ): Result<Boolean> {
        DataTables.getTable(type)?.let { table ->
            val (dir, name) = splitKey(key)
            return runCatching {
                transaction(getOrCreateDBConnection()) {
                    SchemaUtils.create(table)
                    // Saving an existing resource must overwrite it. A bare insert hit the primary
                    // key and threw straight out of this method instead of returning a failure.
                    table.deleteWhere {
                        (table.dir eq dir) and (table.name eq name)
                    }
                    table.insert {
                        it[table.dir] = dir
                        it[table.name] = name
                        it[config] = value
                    }
                }
                true
            }
        }
        return Result.failure(UnsupportedOperationException("Unsupported data type: $type"))
    }

    /**
     * Splits a resource key into its directory and file name.
     *
     * `substringBeforeLast`/`substringAfterLast` both return the WHOLE string when the separator is
     * absent, so a root-level key such as `my_recipe` was stored with dir == name.
     */
    private fun splitKey(key: Key): Pair<String, String> {
        val separator = key.value.lastIndexOf('/')
        if (separator < 0) {
            return "" to key.value
        }
        return key.value.substring(0, separator) to key.value.substring(separator + 1)
    }

    override fun delete(type: DataType<Any>, key: Key): Result<Boolean> {
        DataTables.getTable(type)?.let { table ->
            val (dir, name) = splitKey(key)

            // The transaction's return value used to be discarded and the method always reported
            // "nothing deleted", so a successful delete looked like a no-op to every caller.
            return runCatching {
                transaction(getOrCreateDBConnection()) {
                    SchemaUtils.create(table)
                    table.deleteWhere {
                        (table.dir eq dir) and (table.name eq name)
                    } > 0
                }
            }
        }

        return Result.failure(UnsupportedOperationException("Unsupported data type: $type"))
    }

}
