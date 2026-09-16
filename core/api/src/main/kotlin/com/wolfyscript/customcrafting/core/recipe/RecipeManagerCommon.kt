package com.wolfyscript.customcrafting.core.recipe

import com.wolfyscript.customcrafting.core.CustomCrafting
import com.wolfyscript.customcrafting.core.resource.LoadedObject
import com.wolfyscript.customcrafting.core.resource.ResourceLoader
import com.wolfyscript.customcrafting.core.recipe.RecipeManager.Companion.LOG_PREFIX
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeEvaluationResult
import com.wolfyscript.customcrafting.core.recipe.evaluation.RecipeInput
import com.wolfyscript.customcrafting.core.recipe.evaluation.EvaluationContext
import com.wolfyscript.customcrafting.core.resource.DataType
import com.wolfyscript.customcrafting.core.resource.ResourceListener
import com.wolfyscript.customcrafting.core.util.resourceSubDir
import com.wolfyscript.scafall.ScafallProvider
import com.wolfyscript.scafall.identifier.Key
import com.wolfyscript.scafall.verification.VerificationResult
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyTo
import kotlin.io.path.pathString
import kotlin.io.path.walk

internal class RecipeManagerCommon(val customCrafting: CustomCrafting) : RecipeManager, ResourceListener {

    /**
     * The index object is immutable, but the reference is swapped from the (async) resource-loading
     * path and read from the main-thread craft path, so the field itself must be volatile or a
     * reloading server can keep serving the old index indefinitely.
     */
    @Volatile
    private var index: RecipeIndex = RecipeIndex(emptyList())

    /**
     * Recipes can be loaded by other plugins. We keep track of which recipes CC registers, to not unload third-party recipes, for example, on a reload.
     */
    final override val recipesLoadedByCC: Set<Key>
        field = ObjectOpenHashSet()

    final override val disabledRecipes: Set<Key>
        get() {
            return Collections.unmodifiableSet(backingDisabledRecipes)
        }

    final override val invalidRecipes: List<VerificationResult<CustomRecipe<*, *>>>
        field = mutableListOf()

    val backingDisabledRecipes: MutableSet<Key> = ObjectOpenHashSet()

    val awaitingVerificationRecipes: MutableList<LoadedObject<CustomRecipe<*,*>>> = mutableListOf()
    val scafall = ScafallProvider.get()

    init {
        scafall.dependencyManager.onDependencyInitialized {
            verifyRecipesAndLoad()
        }
    }

    /**
     * Prepare everything before recipes are loaded.
     */
    override fun onPrepare(resourceLoader: ResourceLoader) {
        exportDefaults(resourceLoader)
    }

    private fun exportDefaults(resourceLoader: ResourceLoader) {
        customCrafting.logger.info("${LOG_PREFIX}Exporting default recipes...")
        val dir = "com/wolfyscript/customcrafting/resources/default/recipes"
        val resource = javaClass.classLoader.getResource(dir)?.toURI()
        if (resource == null) {
            customCrafting.logger.error("${LOG_PREFIX}Could not find default recipes!")
            return
        }
        val target = DataType.Recipes.resourceSubDir(File(resourceLoader.directory, "default"))
        target.mkdirs()
        copyToFromFileSystem(resource, dir, target.toPath())
        customCrafting.logger.info("${LOG_PREFIX}Default recipes exported to $target")
    }

    private fun FileSystem.copyTo(dir: String, target: Path) {
        getPath(dir).walk().forEach {
            it.copyTo(target.resolve(it.fileName.pathString), true)
        }
    }

    private fun copyToFromFileSystem(uri: URI, fromDir: String, target: Path) {
        try {
            val fs = FileSystems.getFileSystem(uri)
            // The file system is already open so we shouldn't close it as it may cause issues (e.g. on Fabric)
            fs.copyTo(fromDir, target)
        } catch (_: FileSystemNotFoundException) {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>(), javaClass.classLoader).use { fs ->
                fs.copyTo(
                    fromDir,
                    target
                ) // In this case we control the life-time of the file system, so close it after copying.
            }
        }
    }

    /**
     * How recipes should be loaded on startup
     */
    override fun onInitialLoad(resourceLoader: ResourceLoader) {
        // Each load cycle rebuilds this list. Without the reset, a runtime reload appended a second
        // copy of every recipe key on top of the ones gathered at startup.
        awaitingVerificationRecipes.clear()
        val keysFromEarlierSources = HashSet<Key>()
        resourceLoader.sources.forEach { dest ->
            dest.load(DataType.Recipes) { loaded ->
                // `overwriteExisting` is documented in the shipped resources.conf ("resources loaded
                // from this destination override existing resources with the same path") but used to
                // be read by nobody: every source simply appended and the last one silently won.
                if (!keysFromEarlierSources.add(loaded.key)) {
                    if (!dest.settings.overwriteExisting) {
                        customCrafting.logger.debug(
                            "{}skipped: {} (already provided by an earlier source, and this one does not overwrite)",
                            LOG_PREFIX, loaded.key
                        )
                        return@load
                    }
                    awaitingVerificationRecipes.removeIf { existing -> existing.key == loaded.key }
                }
                customCrafting.logger.debug("{}loaded: {} -> {}", LOG_PREFIX, loaded.key, loaded.value)
                awaitingVerificationRecipes.add(loaded)
            }
        }
    }

    /**
     * How recipes should be loaded when reloaded at runtime.
     * This should run on a separate thread, async to the main thread.
     */
    override fun onReload(resourceLoader: ResourceLoader) {
        // Re-read every source. onInitialLoad resets the pending list first, and onFinalize then
        // verifies what came back and drops whatever disappeared; that is exactly a reload.
        // The dedicated hook exists so a reload does NOT re-run the first-startup path.
        onInitialLoad(resourceLoader)
    }

    /**
     * Finalize the loaded recipes (for which the dependencies are already available).
     * Verify them, add them to the manager, and remove any recipes that were previously loaded and no longer loaded. (important for reloads)
     */
    override fun onFinalize(resourceLoader: ResourceLoader) {
        val previousLoaded = recipesLoadedByCC.toSet()
        recipesLoadedByCC.clear()

        verifyRecipesAndLoad()

        // Remove recipes that are no longer loaded
        val removed = previousLoaded.subtract(recipesLoadedByCC)
        if (removed.isNotEmpty()) {
            customCrafting.logger.info("${LOG_PREFIX}Removing ${removed.size} recipes that are no longer loaded")
            removeRecipes(*removed.toTypedArray())
        }
    }

    private fun verifyRecipesAndLoad() {
        customCrafting.logger.info("${LOG_PREFIX}Verifying ${awaitingVerificationRecipes.size} recipes")
        for (loadedRecipe in awaitingVerificationRecipes) {
            // TODO: verify recipe
            recipesLoadedByCC.add(loadedRecipe.key)
        }
        // Register on THIS manager directly.
        //
        // This used to go `customCrafting.server?.recipeManager?.registerOrUpdateRecipes(...)`, which
        // is a round trip to this very object through a nullable gate: `RecipeManagerCommon` is built
        // inside the CustomCraftingServer constructor, so while that constructor runs
        // `customCrafting.server` is still null. When the `onDependencyInitialized` hook above fired
        // in that window, the `?.` swallowed the whole registration — while the loop above had
        // ALREADY added every key to `recipesLoadedByCC`.
        //
        // The result was a recipe that reports as loaded in `/recipes status` but is absent from the
        // index: it never matches when crafting and `getRecipe()` returns null, so the editor cannot
        // open it either.
        registerOrUpdateRecipes(awaitingVerificationRecipes)
    }

    override fun <I : RecipeInput, D : RecipeEvaluationResult.Data, T : CustomRecipe<I, D>> evaluateRecipesOfType(
        type: RecipeType<T>,
        input: I,
        context: EvaluationContext,
    ): RecipeEvaluationResult<D, T>? {
        val recipes: Collection<RecipeReference<T>> = index.byType(type)
        for (recipe in recipes) {
            if (isRecipeDisabled(recipe.key)) {
                continue
            }
            val data = recipe.value?.evaluate(input, context) ?: continue
            return RecipeEvaluationResult.of(recipe, data)
        }
        return null
    }

    override fun disableRecipe(recipe: Key) {
        val ref = index.get(recipe)
        if (ref != null) {
            backingDisabledRecipes.add(recipe)
        }
    }

    override fun enableRecipe(key: Key) {
        backingDisabledRecipes.remove(key)
    }

    override fun isRecipeDisabled(key: Key): Boolean {
        return backingDisabledRecipes.contains(key)
    }

    override fun getRecipe(key: Key): RecipeReference<*>? {
        return index.get(key)
    }

    override fun registerOrUpdateRecipes(recipes: Collection<LoadedObject<CustomRecipe<*, *>>>) {
        index = index.registerOrUpdateAll(recipes)
    }

    override fun removeRecipes(vararg recipes: Key) {
        index = index.removeBatch(*recipes)
    }

    override fun recipes(): Collection<RecipeReference<*>> {
        return index.values()
    }

}