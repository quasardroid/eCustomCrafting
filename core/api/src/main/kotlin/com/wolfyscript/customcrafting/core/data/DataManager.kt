package com.wolfyscript.customcrafting.core.data

import java.io.File

interface DataManager {

    companion object {

        const val DATA_PATH = ".data"

        fun createNewForDir(directory: File): DataManager {
            return DataManagerImpl(directory)
        }

    }

    val storageDir: File

}