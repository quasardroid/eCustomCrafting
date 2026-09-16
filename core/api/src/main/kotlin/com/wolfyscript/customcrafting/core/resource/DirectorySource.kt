package com.wolfyscript.customcrafting.core.resource

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.InjectableValues
import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.configuration.resources.SourceSettings
import com.wolfyscript.customcrafting.core.util.CUSTOMCRAFTING_NAMESPACE
import com.wolfyscript.customcrafting.core.util.resourceSubDir
import com.wolfyscript.scafall.identifier.Key
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.pathString

internal class DirectorySource(
    val customCrafting: CustomCrafting,
    resourceLoaderImpl: ResourceLoader,
    override val settings: SourceSettings.DirectorySourceSettings,
) : Source {

    val directory: File

    init {
        // An omitted `path` means "the resource loader's own directory". Defaulting to its path
        // STRING and then running it through the relative branch appended that directory to itself
        // (plugins/CustomCrafting/resources/plugins/CustomCrafting/resources), because Bukkit's
        // dataFolder is relative.
        directory = settings.path?.let { configured ->
            val resolvedPath = Paths.get(configured)
            if (resolvedPath.isAbsolute) {
                File(configured)
            } else {
                File(resourceLoaderImpl.directory, resolvedPath.pathString)
            }
        } ?: resourceLoaderImpl.directory
    }

    override val filter: Source.Filter? =
        settings.filter?.let { DestinationFilter(customCrafting, it) }

    private fun assureDir() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    override fun <T : Any> load(
        type: DataType<T>,
        accept: (value: LoadedObject<T>) -> Unit,
    ) {
        assureDir()
        readFiles(type.resourceSubDir(directory)) { relative: Path, file: Path, attrs: BasicFileAttributes ->
            val injectableValues = InjectableValues.Std().apply {
                addValue("customCrafting", customCrafting)
                addValue(CustomCrafting::class.java, customCrafting)
            }

            val key = relative.toKey(Key.CUSTOMCRAFTING_NAMESPACE)
            // Per-FILE logging at info level floods the console and, because Log4j appenders are
            // synchronised, contends with whatever the main thread is logging.
            customCrafting.logger.debug("Loading {}: {}", type.id, key)
            try {
                val value = customCrafting.server!!.resourceManager.jacksonObjectMapper
                    .reader(injectableValues)
                    .readValue(file.toFile(), type.classType)

                accept(ResourceLoaderImpl.LoadedObjectImpl(key, value))
            } catch (e: Exception) {
                customCrafting.logger.error("Error loading $key from ${type.id}: ", e)
            }
            return@readFiles FileVisitResult.CONTINUE
        }
    }

    override fun <T : Any> save(type: DataType<T>, key: Key, value: T): Result<Boolean> {
        assureDir()
        val destFile = resolveContained(type, key).getOrElse { return Result.failure(it) }

        if (destFile.getParentFile().exists() || destFile.getParentFile().mkdirs()) {
            try {
                if (destFile.isFile() || destFile.createNewFile()) {
                    customCrafting.server!!.resourceManager.jacksonObjectMapper.writer(DefaultPrettyPrinter())
                        .writeValue(destFile, value)
                    return Result.success(true)
                }
            } catch (e: IOException) {
                return Result.failure(e)
            }
        }
        return Result.failure(Exception("Could not create file $destFile to save recipe $key!"))
    }

    /**
     * Resolves the file backing [key], refusing anything that escapes this source's directory.
     *
     * A key value may legally contain `.` and `/`, and keys reach here straight from the editor's
     * "save as", so `customcrafting:../../../../plugins/Other/config` used to resolve — and be
     * written — outside the resources tree.
     */
    private fun resolveContained(type: DataType<*>, key: Key): Result<File> {
        val root = type.resourceSubDir(directory).toPath().toAbsolutePath().normalize()
        val dest = root.resolve("${key.value}.conf").normalize()
        if (!dest.startsWith(root)) {
            return Result.failure(
                IllegalArgumentException("Resource key '$key' resolves outside of $root and was rejected")
            )
        }
        return Result.success(dest.toFile())
    }

    override fun delete(type: DataType<Any>, key: Key): Result<Boolean> {
        val destFile = resolveContained(type, key).getOrElse { return Result.failure(it) }

        return try {
            Result.success(destFile.delete())
        } catch (e: Exception) {
            Result.failure(Exception("Could not delete file $directory to delete recipe $key!", e))
        }
    }

    private fun readFiles(rootDir: File, visitor: NamespaceFileVisitor.CustomFileVisitor) {
        customCrafting.logger.info("Read files in ${rootDir.path}")
        if (!rootDir.exists()) return
        try {
            val root = rootDir.toPath()
            Files.walkFileTree(root, NamespaceFileVisitor(root, visitor))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private class NamespaceFileVisitor(val root: Path, val visitor: CustomFileVisitor) : SimpleFileVisitor<Path>() {

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            return visitor.visit(root.relativize(file), file, attrs)
        }

        fun interface CustomFileVisitor {

            fun visit(relative: Path, file: Path, attrs: BasicFileAttributes): FileVisitResult

        }

    }

    fun Path.toKey(namespace: String): Key {
        var pathString = this.toString()
        if (!File.separator.equals("/")) {
            // #205: Required to work with Windows file separators (And possibly other separators).
            pathString = pathString.replace(File.separatorChar, '/')
        }
        // A file with no dot in its relative path (README, a stray `backup`) gives lastIndexOf == -1,
        // and String.take(-1) throws. That happened outside the caller's try/catch and killed the
        // whole directory walk, so one such file stopped every recipe from loading.
        val extensionStart = pathString.lastIndexOf('.')
        val value = if (extensionStart > 0) pathString.substring(0, extensionStart) else pathString
        return Key.key(namespace, value)
    }


}
