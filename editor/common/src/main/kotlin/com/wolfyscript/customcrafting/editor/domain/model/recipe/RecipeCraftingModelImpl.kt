package com.wolfyscript.customcrafting.editor.domain.model.recipe

import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.CustomIngredientModelImpl
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.IngredientModelRefImpl
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.RecipeChoicesModelImpl
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.ResultModelImpl
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.IngredientModel
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.IngredientModelRef
import com.wolfyscript.customcrafting.editor.domain.model.recipe.item.ResultModel
import com.wolfyscript.customcrafting.core.recipe.*
import com.wolfyscript.customcrafting.core.recipe.condition.RecipeConditions
import com.wolfyscript.customcrafting.core.recipe.ingredient.Ingredient
import kotlin.text.isBlank

internal class RecipeCraftingModelFactory : RecipeModel.RecipeTypeSpecificModel.Factory<CustomRecipeCrafting> {

    override val recipeType: RecipeType<CustomRecipeCrafting> by lazy { RecipeTypes.crafting.resolveOrThrow() }

    override fun edit(recipe: CustomRecipeCrafting): RecipeModel.RecipeTypeSpecificModel<CustomRecipeCrafting> {
        // This used to hand back an EMPTY model, so opening an existing recipe and saving it
        // replaced that recipe with a blank one.
        //
        // NOT yet carried over, because no model loader exists for them: the result's actions and
        // bulk actions, and the item modifier's transformations. Saving an edited recipe still drops
        // those. Everything below IS restored.
        val model = RecipeCraftingModelImpl()
        val collection = model.ingredientCollection

        when (val formula = recipe.formula) {
            is CraftingFormula.Shapeless -> {
                model.setFormulaType(CraftingFormula.Shapeless::class.java)
                val shapeless = model.formula as ShapelessCraftingFormulaModel
                for ((index, ingredient) in formula.ingredients.withIndex()) {
                    collection.add(CustomIngredientModelImpl.loadFrom(ingredient))
                    shapeless.assignIngredient(index, index)
                }
            }

            is CraftingFormula.Shaped -> {
                model.setFormulaType(CraftingFormula.Shaped::class.java)
                val shaped = model.formula as ShapedCraftingFormulaModel
                for (ingredient in formula.ingredients) {
                    collection.add(CustomIngredientModelImpl.loadFrom(ingredient))
                }
                // `ingredients` is ordered by first appearance in the shape, which is exactly the
                // order of `shape.ingredientIndices`, so the char maps to the collection index.
                val shape = formula.shape
                for ((rowIndex, row) in shape.rows.withIndex()) {
                    if (rowIndex >= 3) break
                    for ((columnIndex, symbol) in row.withIndex()) {
                        if (columnIndex >= 3 || symbol.isWhitespace()) continue
                        val collectionIndex = shape.ingredientIndices.indexOf(symbol)
                        if (collectionIndex < 0) continue
                        shaped.assignIngredient(rowIndex * 3 + columnIndex, collectionIndex)
                    }
                }
                shaped.shape.symmetry = shape.symmetry
                shaped.shape.trim = shape.trim
            }

            else -> {}
        }

        model.result = ResultModelImpl(
            choices = RecipeChoicesModelImpl(
                recipe.result.choices.stacks.toMutableList(),
                recipe.result.choices.tags.toMutableList(),
            ),
            alwaysKeepPrevious = recipe.result.alwaysKeepPrevious,
        )

        return model
    }

    override fun create(): RecipeModel.RecipeTypeSpecificModel<CustomRecipeCrafting> {
        return RecipeCraftingModelImpl()
    }

}

internal data class IngredientCollectionModelImpl(
    override val ingredients: MutableList<IngredientModel> = mutableListOf(),
) : RecipeCraftingModel.IngredientCollectionModel {

    /**
     * Called after an ingredient is removed so the formula can repair its positional references.
     * Set by [RecipeCraftingModelImpl]; the collection itself does not know about the formula.
     */
    internal var onIngredientRemoved: ((removedIndex: Int) -> Unit)? = null

    override fun addNew() {
        add(CustomIngredientModelImpl())
    }

    override fun add(ingredient: IngredientModel) {
        if (ingredients.size < 9) {
            ingredients.add(ingredient)
        }
    }

    override fun set(
        index: Int,
        ingredient: IngredientModel,
    ) {
        ingredients[index] = ingredient
    }

    override fun remove(index: Int) {
        // Bounds-check: this is driven straight from a GUI slot index.
        if (index < 0 || index >= ingredients.size) {
            return
        }
        ingredients.removeAt(index)
        // Every later ingredient just shifted down one position, and the formula stores positional
        // references. Without this the formula silently pointed at the wrong ingredients (or past
        // the end of the list) after any removal.
        onIngredientRemoved?.invoke(index)
    }

}

internal data class RecipeCraftingModelImpl(
    override var result: ResultModel = ResultModelImpl(),
    override var formula: RecipeCraftingModel.CraftingFormulaModel<*> = ShapedCraftingFormulaModel(),
    override val ingredientCollection: RecipeCraftingModel.IngredientCollectionModel = IngredientCollectionModelImpl(),
) : RecipeCraftingModel {

    init {
        // Keep the formula's positional references in step with the ingredient list.
        (ingredientCollection as? IngredientCollectionModelImpl)?.onIngredientRemoved = { removedIndex ->
            repairRefsAfterRemoval(removedIndex)
        }
    }

    /**
     * Drops references to the ingredient that was removed and renumbers everything after it.
     */
    private fun repairRefsAfterRemoval(removedIndex: Int) {
        when (val current = formula) {
            is RecipeCraftingModel.CraftingFormulaModel.Shaped -> {
                for (i in current.ingredientRefs.indices) {
                    val ref = current.ingredientRefs[i] ?: continue
                    current.ingredientRefs[i] = when {
                        ref.indexInCollection == removedIndex -> null
                        ref.indexInCollection > removedIndex -> IngredientModelRefImpl(ref.indexInCollection - 1)
                        else -> ref
                    }
                }
            }

            is RecipeCraftingModel.CraftingFormulaModel.Shapeless -> {
                current.ingredientRefs.removeAll { it.indexInCollection == removedIndex }
                for (i in current.ingredientRefs.indices) {
                    val ref = current.ingredientRefs[i]
                    if (ref.indexInCollection > removedIndex) {
                        current.ingredientRefs[i] = IngredientModelRefImpl(ref.indexInCollection - 1)
                    }
                }
            }

            else -> {}
        }
    }

    override fun setFormulaType(type: Class<out CraftingFormula>) {
        val ingredients = when (val previousFormula = formula) {
            is RecipeCraftingModel.CraftingFormulaModel.Shaped -> previousFormula.ingredientRefs
            is RecipeCraftingModel.CraftingFormulaModel.Shapeless -> previousFormula.ingredientRefs
            else -> emptyList()
        }

        formula = when (type) {
            CraftingFormula.Shaped::class.java -> {
                val newList = ArrayList<IngredientModelRef?>(9)
                for (i in 0 until 9) {
                    newList.add(ingredients.getOrElse(i) { null })
                }
                ShapedCraftingFormulaModel(ingredientRefs = newList)
            }

            CraftingFormula.Shapeless::class.java -> ShapelessCraftingFormulaModel(
                ingredientRefs = ingredients.filterNotNull().toMutableList()
            )

            else -> ShapedCraftingFormulaModel()
        }
    }

    override fun complete(common: RecipeModel<CustomRecipeCrafting>): Result<CustomRecipeCrafting> {
        val completedFormula = formula.complete(ingredientCollection).getOrElse {
            return Result.failure(IllegalStateException("Failed to create crafting recipe: Invalid formula", it))
        }
        val completedResult = result.complete().getOrElse {
            return Result.failure(IllegalStateException("Failed to create crafting recipe: Invalid result", it))
        }

        val recipe = CustomRecipeCrafting.of(
            group = "", // TODO
            priority = common.priority,
            conditions = common.condition?.complete()?.getOrNull() ?: RecipeConditions.of(),
            formula = completedFormula,
            result = completedResult
        )
        return Result.success(recipe)
    }

}

internal data class ShapelessCraftingFormulaModel(
    override val ingredientRefs: MutableList<IngredientModelRef> = mutableListOf(),
) : RecipeCraftingModel.CraftingFormulaModel.Shapeless {

    override fun assignIngredient(
        index: Int,
        collectionIndex: Int,
    ) {
        if (index >= 0 && index < ingredientRefs.size) {
            ingredientRefs[index] = IngredientModelRefImpl(collectionIndex)
        } else if (index >= ingredientRefs.size && ingredientRefs.size < 9) {
            // `>` skipped the append-at-the-end case, so on an empty list slot 0 matched neither
            // branch and the first ingredient could never be assigned.
            ingredientRefs.add(IngredientModelRefImpl(collectionIndex))
        }
    }

    override fun unassignIngredient(index: Int) {
        // Bound the INDEX, not the list size: `size < 9` let any out-of-range index through and
        // removeAt threw IndexOutOfBoundsException.
        if (index >= 0 && index < ingredientRefs.size) {
            ingredientRefs.removeAt(index)
        }
    }

    override fun complete(collection: RecipeCraftingModel.IngredientCollectionModel): Result<CraftingFormula.Shapeless> {
        val completedIngredients = mutableListOf<Ingredient>()
        for ((index, ref) in ingredientRefs.withIndex()) {
            val resolved = ref.resolveFor(collection)
                ?: return Result.failure(IllegalStateException("Failed to resolve ingredient $ref: Missing ingredient in collection"))
            val ingredient = resolved.complete().getOrElse {
                return Result.failure(
                    IllegalStateException(
                        "Failed to create shapeless formula: invalid ingredient at index $index",
                        it
                    )
                )
            }
            completedIngredients.add(ingredient)
        }

        if (completedIngredients.isEmpty()) {
            return Result.failure(IllegalStateException("Failed to create shapeless formula: Must have at least 1 ingredient"))
        }

        return Result.success(CraftingFormula.Shapeless.of(completedIngredients))
    }

}

internal data class ShapedCraftingFormulaModel(
    // TODO: Make immutable
    override val ingredientRefs: MutableList<IngredientModelRef?> = arrayOfNulls<IngredientModelRef?>(9).toMutableList(),
    override var shape: RecipeCraftingModel.CraftingFormulaModel.Shaped.ShapeModel = ShapeModel(
        ingredientRefs
    ),
) : RecipeCraftingModel.CraftingFormulaModel.Shaped {

    override fun assignIngredient(
        index: Int,
        collectionIndex: Int,
    ) {
        if (index >= 0 && index < ingredientRefs.size) {
            ingredientRefs[index] = IngredientModelRefImpl(collectionIndex)
        }
    }

    override fun unassignIngredient(index: Int) {
        if (index >= 0 && index < ingredientRefs.size) {
            ingredientRefs[index] = null
        }
    }

    override fun complete(collection: RecipeCraftingModel.IngredientCollectionModel): Result<CraftingFormula.Shaped> {
        val mappedIngredients = buildMap {
            for (ref in ingredientRefs) {
                // An unassigned slot is an EMPTY cell of the shape, not an error. Treating null as a
                // failure meant a shaped recipe could only be saved with all 9 slots filled, i.e.
                // never for a normal recipe. The emptiness case is covered by the check below.
                if (ref == null) {
                    continue
                }
                val resolvedIngredient = ref.resolveFor(collection)
                    ?: return Result.failure(IllegalStateException("Failed to resolve ingredient for $ref: Missing ingredient in collection"))
                val shapeId = ref.toShapeId()
                this[shapeId] = resolvedIngredient.complete().getOrElse {
                    return Result.failure(
                        IllegalStateException(
                            "Failed to create shaped formula: invalid ingredient at index ${ref.indexInCollection}",
                            it
                        )
                    )
                }
            }
        }
        if (mappedIngredients.isEmpty()) {
            return Result.failure(IllegalStateException("Failed to create shaped formula: Must have at least 1 ingredient"))
        }
        val completedShape = shape.complete().getOrElse {
            return Result.failure(
                IllegalStateException(
                    "Failed to create shaped formula: failed to complete shape",
                    it
                )
            )
        }
        if (completedShape.rows.all { it.isBlank() }) {
            return Result.failure(IllegalStateException("Failed to create shaped formula: Must have a defined shape"))
        }

        val shaped = CraftingFormula.Shaped.of(mappedIngredients, completedShape)
        return Result.success(shaped)
    }

    class ShapeModel(
        val ingredientRefs: List<IngredientModelRef?>,
        override var symmetry: CraftingFormula.Shaped.ShapeSymmetry = CraftingFormula.Shaped.ShapeSymmetry.of(
            horizontal = false,
            vertical = false,
            rotate = false
        ),
        override var trim: Boolean = true,
    ) : RecipeCraftingModel.CraftingFormulaModel.Shaped.ShapeModel {

        override fun complete(): Result<CraftingFormula.Shaped.Shape> {
            val rows: MutableList<String> = mutableListOf("", "", "")
            for ((index, ref) in ingredientRefs.withIndex()) {
                rows[index / 3] += ref?.toShapeId() ?: ' '
            }
            return Result.success(CraftingFormula.Shaped.Shape.of(rows, trim, symmetry))
        }
    }

}

