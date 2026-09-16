package com.wolfyscript.customcrafting.core.data

import java.io.File

internal class DataManagerImpl(rootDir: File) : DataManager {

    // The path lives on the DataManager interface; a second copy here could drift from it.
    override val storageDir: File = File(rootDir, DataManager.DATA_PATH)

}