package com.wolfyscript.customcrafting.core.util

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.resource.DataType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val LOGGER = LoggerFactory.getLogger("CustomCrafting/Resources")

fun getResourceAsStream(pathToResource: String): InputStream? {
    return CustomCrafting::class.java.classLoader.getResourceAsStream(pathToResource)
}

/**
 * Copies a resource bundled in the jar to [outDest].
 *
 * The destination is only created once the resource has been found and fully written. The previous
 * version created the file first and returned silently when the resource was missing or the copy
 * failed, leaving a zero-byte config behind — and since the caller only exports when the file does
 * NOT exist, that empty file was then kept forever and failed to parse on every later start.
 *
 * @return true when the resource was written.
 */
fun exportResource(pathToResource: String, outDest: File): Boolean {
    val inputStream = getResourceAsStream(pathToResource)
    if (inputStream == null) {
        LOGGER.error("Cannot export resource '$pathToResource': not found on the classpath")
        return false
    }

    val parent = outDest.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
        LOGGER.error("Cannot export resource '$pathToResource': failed to create directory $parent")
        inputStream.close()
        return false
    }

    // Write beside the target and move into place, so a half-written copy never becomes the config.
    val tempFile = File(parent, "${outDest.name}.tmp")
    try {
        inputStream.use { input ->
            FileOutputStream(tempFile).use { out ->
                input.copyTo(out)
            }
        }
        Files.move(tempFile.toPath(), outDest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return true
    } catch (ex: IOException) {
        LOGGER.error("Failed to export resource '$pathToResource' to $outDest", ex)
        tempFile.delete()
        return false
    }
}

fun DataType<*>.resourceSubDir(dir: File): File {
    return File(dir, this.id)
}
