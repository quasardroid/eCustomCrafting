package com.wolfyscript.customcrafting.core.resource.database

import com.wolfyscript.customcrafting.core.resource.DataType
import java.util.concurrent.ConcurrentHashMap

internal object DataTables {

    // Shared between the loading path and every save/delete, which do not all run on one thread.
    private val tables: MutableMap<DataType<*>, JsonValueTable<*>> = ConcurrentHashMap()

    fun <T: Any> getTable(type: DataType<T>): JsonValueTable<T>? {
        return tables.getOrPut(type) {
            JsonValueTable(type.id, type.classType)
        } as JsonValueTable<T>?
    }

}

