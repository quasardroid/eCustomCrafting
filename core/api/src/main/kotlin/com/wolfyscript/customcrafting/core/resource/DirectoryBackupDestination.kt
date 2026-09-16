package com.wolfyscript.customcrafting.core.resource

import com.wolfyscript.customcrafting.core.configuration.resources.BackupSettings
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.pathString

internal class DirectoryBackupDestination(
    val resourceLoader: ResourceLoader,
    val settings: BackupSettings.DirectoryBackupDestinationSettings,
) : BackupDestination {

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }

    val path: String = settings.path
    val directory: File = File(resourceLoader.directory.toPath().resolve(path).pathString)

    // The IO below can throw (ZipException on overlapping sources, IOException on permissions);
    // turn that into a failed Result instead of letting it escape and abort every other destination.
    override fun backup(): Result<File> = runCatching { createBackup() }.getOrElse { Result.failure(it) }

    /**
     * Deletes the oldest backups until at most [BackupSettings.DirectoryBackupDestinationSettings.keep]
     * remain. `keep <= 0` means unlimited.
     *
     * The setting was documented in the shipped config ("Keep the past 8 backups") but nothing ever
     * enforced it, so backups grew without bound.
     */
    private fun pruneOldBackups() {
        val keep = settings.keep
        if (keep <= 0) {
            return
        }
        val existing = directory.listFiles()?.filter { entry ->
            // Only touch entries this destination created: their name IS the timestamp.
            runCatching { LocalDateTime.parse(entry.nameWithoutExtension, dateFormat) }.isSuccess
        } ?: return

        existing.sortedByDescending { LocalDateTime.parse(it.nameWithoutExtension, dateFormat) }
            .drop(keep)
            .forEach { it.deleteRecursively() }
    }

    private fun createBackup(): Result<File> {
        val date = LocalDateTime.now()
        val backupName = dateFormat.format(date)

        if (!directory.exists() && !directory.mkdirs()) {
            return Result.failure(Exception("Failed to create backup directory $directory"))
        }

        if (settings.compress) {
            val zipBackupFile = File(directory, "$backupName.zip")
            ZipOutputStream(zipBackupFile.outputStream()).use { zipOutputStream ->
                for (source in resourceLoader.sources) {
                    if (source is DirectorySource) {
                        source.directory.walkTopDown()
                            .filter { it.isFile }
                            .forEach { file ->
                                val zipEntry = ZipEntry(
                                    // relative path including the root directory
                                    source.directory.parentFile.toPath().relativize(file.toPath()).pathString
                                )
                                zipOutputStream.putNextEntry(zipEntry)
                                file.inputStream().use { it.copyTo(zipOutputStream) }
                                zipOutputStream.closeEntry()
                            }
                    }
                }
            }
            pruneOldBackups()
            return Result.success(zipBackupFile)
        } else {
            val backupDirectory = File(directory, backupName)
            if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
                return Result.failure(Exception("Failed to create backup directory $backupDirectory"))
            }

            for (source in resourceLoader.sources) {
                if (source is DirectorySource) {
                    source.directory.copyRecursively(File(backupDirectory, source.directory.name), overwrite = true)
                }
            }
            pruneOldBackups()
            return Result.success(backupDirectory)
        }
    }

}