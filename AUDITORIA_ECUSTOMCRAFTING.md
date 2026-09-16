# Auditoría de bugs, correctitud y rendimiento — eCustomCrafting (fork `quasardroid`, rama `v-5`)

**Sujeto:** `CustomCrafting 5.0-alpha.4.0.0` — 299 ficheros fuente, ~17 000 líneas (Kotlin + Java),
multi-módulo Gradle sobre el framework `scafall`. Plataformas: Spigot, Paper, Fabric.

**Naturaleza:** solo diagnóstico. **No se modificó ni una línea de código.** El único artefacto es este informe.

**Metodología** (importada de las auditorías de `D:\Github\ePlugins`): lectura del código real, no
inferencia por nombres, trazando cada cadena de llamada hasta el hilo y el evento concretos.
**8 lectores en paralelo** cubrieron el árbol por rebanadas coherentes (313 ficheros abiertos en total);
**cada hallazgo Crítico/Alto pasó por un verificador adversarial independiente** que volvió a abrir el
código con el encargo explícito de refutarlo y la instrucción de refutar por defecto ante la duda.
De **116 hallazgos brutos**, **115 sobreviven**, **1 fue refutado** (§Apéndice A) y
**10 vieron su severidad corregida** (§Apéndice B). Contraste manual adicional del auditor sobre
`CraftingMatrixDataImpl.kt`, `AnvilListener.kt` y `StonecutterListener.kt`.

> **Nota sobre el alcance del fork.** Todo lo que sigue son defectos del código v5 tal y como está en
> este fork; no se comparó contra el upstream `WolfyScript/CustomCrafting`, así que no se afirma nada
> sobre cuáles son propios del fork y cuáles heredados. El proyecto está en alpha temprana y lo dice su
> README — "esto está incompleto" no se ha reportado como hallazgo, pero varias de las rutas rotas de
> abajo son *funciones que no pueden funcionar nunca*, no huecos por rellenar.

## Estado de remediación (2026-09-16)

**Aplicado: 113 de los 115 hallazgos.** Los 6 Críticos, los 34 Altos, y las dos tandas completas —
primero la de bugs (`bug`, `correctness`, `concurrency`, `security`, `api-misuse`, `config`) y
después la de optimización (`performance`, `memory-leak`, `resource-leak`, `dead-code`,
`simplification`).

**Verificación:** `compileKotlin` + `compileJava` en verde para `core-api`, `core-common`,
`core-spigotlike`, `core-spigot`, `core-paper`, `core-fabric` y `editor-common`, con Gradle 9.5.1 y
JDK 25. **Nada se ha probado en un servidor real, y `ui-common` no se puede compilar** (su
dependencia `viewportl` no está publicada), así que sus 9 arreglos van sin verificar.

### No aplicado (2)

- **BUILD-7** (`core/fabric/build.gradle.kts`, `implementation` en vez de `modImplementation`) —
  **no se puede aplicar tal como está descrito**: en este proyecto la configuración
  `modImplementation` no existe. Gradle responde *"Configuration with name 'modImplementation' not
  found"*, y `:core:core-fabric:dependencies` solo expone `include` de las configuraciones de Loom.
  Cambiarlo requiere primero averiguar cómo aplica Loom el convention plugin
  `build.settings.fabric-loom`; se dejó como está para no romper el build de Fabric.
- **FABRIC-5** (proxies construidos en `RecipeManager.prepare()`, que corre en un worker) — **no se
  movió a la fase `apply`**, porque no hay forma de verificar aquí que el nuevo target de mixin
  resuelva, y fallar ahí rompería el mod entero. En su lugar se cerró la parte real de seguridad de
  hilos: `RecipeManagerCommon.index` ahora es `@Volatile` (RECIPE-18), que es lo único del estado
  compartido que ese código toca, y se documentó la restricción en el propio mixin.

### Aplicado con matices

- **UI-9** (la rejilla 3x3 de shapeless mapea el slot a una lista compacta) — el defecto de datos
  está cerrado vía EDITOR-3: la primera casilla ya se puede asignar y un slot fuera de rango añade al
  final en vez de quedar en el limbo. Lo que queda es cosmético: en modo shapeless el ítem aparece en
  la celda compacta, no en la que se clicó. Rehacer esa vista es un rediseño de UI.
- **EDITOR-10** (editar una receta existente cargaba un modelo en blanco) — ahora se reconstruyen
  ingredientes, fórmula, forma, simetría y las opciones del resultado. **Siguen sin traspasarse las
  acciones y las transformaciones del modificador**, porque no existe un loader de modelo para ellas;
  queda anotado en el propio código.
- **EDITOR-5** (las sesiones del editor nunca se borraban) — el módulo del editor es agnóstico de
  plataforma y no tiene hook de desconexión, así que en vez de un listener de `PlayerQuitEvent` se
  implementó **caducidad por inactividad** (1 h) dentro de `SessionManagerImpl`, sin dependencias
  nuevas. Un hook de quit real sigue siendo lo correcto cuando el módulo tenga ciclo de vida.
- **CFG-16 / CFG-10** — `ConfigurationManagerImpl.load()` ahora hace el parseo de verdad (antes
  ocurría en el constructor) y `ResourceLoader` gana un `reloadResources()` que dispara `onReload`.
  Eso **obligó a reordenar el arranque de Fabric**: `configurationManager.load()` tiene que correr
  antes de `initServer`, porque `initServer` construye el `ResourceManager` a partir de
  `resourceSettings`.

### Hallazgos arreglados que la auditoría no vio (4)

1. `CraftingFormulaShapedImpl.kt` `ShapeImpl.init` — el contador `index` avanzaba un carácter por
   celda, así que una fila más corta que la más ancha desplazaba a la izquierda todas las siguientes.
   Ahora indexa por `r * width + c`.
2. `RecipeUtils.possibleResultAmount` — `minOf` sobre una lista de ingredientes vacía lanzaba
   `NoSuchElementException` dentro del evento de clic. Ahora devuelve 0.
3. `SessionManagerImpl.getOrCreateSession` — `containsKey` + `get()!!` podía dar NPE; ahora es
   `computeIfAbsent` sobre un `ConcurrentHashMap`.
4. `SQLSource.getOrCreateDBConnection` — check-then-put podía abrir (y filtrar) un segundo pool para
   la misma base de datos.

---

## Conteo por severidad

| Severidad | Nº |
|---|---|
| 🔴 Crítico | 6 |
| 🟠 Alto | 34 |
| 🟡 Medio | 57 |
| ⚪ Bajo | 18 |
| **Total** | **115** |

| Rebanada | Ficheros leídos | 🔴 | 🟠 | 🟡 | ⚪ |
|---|---:|---:|---:|---:|---:|
| `RECIPE` | 48 | 2 | 4 | 10 | 2 |
| `CFG` | 35 | 0 | 5 | 9 | 2 |
| `CORE` | 31 | 0 | 3 | 5 | 4 |
| `BUKKIT` | 28 | 4 | 9 | 7 | 1 |
| `FABRIC` | 46 | 0 | 3 | 8 | 2 |
| `EDITOR` | 40 | 0 | 5 | 8 | 2 |
| `UI` | 36 | 0 | 3 | 5 | 1 |
| `BUILD` | 49 | 0 | 2 | 5 | 4 |

## Lo que hay que arreglar primero

Los seis Críticos son, todos, **duplicación o destrucción de ítems**, y son alcanzables por un jugador
sin permisos con una mesa vanilla. En un servidor en producción esto es economía rota el día uno.

1. **BUKKIT-1** — Anvil shift-click duplicates the result: item is added to the inventory AND put on the cursor  
   `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/AnvilListener.kt:99`
1. **BUKKIT-2** — Smithing shift-click duplicates the result exactly like the anvil path  
   `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/SmithingListener.kt:136`
1. **BUKKIT-3** — Stonecutter result handler fires on player-inventory slot 1 because it checks the top inventory instead of the clicked one  
   `core/paper/src/main/kotlin/com/wolfyscript/customcrafting/paper/recipes/StonecutterListener.kt:62`
1. **BUKKIT-4** — Grindstone: inverted isSimilar check merges a foreign result onto the cursor and consumes ingredients even when nothing was collected  
   `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/GrindstoneListener.kt:72`
1. **RECIPE-1** — Trimmed crafting matrix is built with broken index arithmetic and uses maxColumn as the column offset, so every recipe smaller than the grid reads the wrong slots  
   `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/evaluation/CraftingMatrixDataImpl.kt:42`
1. **RECIPE-2** — Smithing recipes report a match even when the base/template/addition items do not match their ingredients  
   `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeSmithingImpl.kt:45`

Y dos frentes que no son Críticos pero bloquean el producto entero:

- **EDITOR-1 + EDITOR-6 + UI-1 + UI-3** — el editor de recetas **no puede guardar una receta con forma**
  (exige las 9 casillas llenas), los botones de edición de ingrediente no abren nada, y cada jugador
  puede crear exactamente **una** receta por arranque del servidor porque la sesión nunca se libera.
- **BUILD-1 + BUILD-2** — el workflow de release publica el `shadowJar` vacío del proyecto raíz en vez
  de los jars de plataforma, y `Exposed` (usado en runtime) no se sombrea ni se declara en `plugin.yml`.

## Reparto por categoría

| Categoría | Nº |
|---|---:|
| `bug` | 58 |
| `correctness` | 15 |
| `config` | 11 |
| `dead-code` | 7 |
| `performance` | 7 |
| `concurrency` | 5 |
| `resource-leak` | 3 |
| `security` | 3 |
| `memory-leak` | 3 |
| `api-misuse` | 2 |
| `simplification` | 1 |

### Índice de optimización y limpieza (21 hallazgos)

Rendimiento, fugas de memoria y de recursos, código muerto y simplificación. El detalle completo de
cada uno está en la sección de su rebanada.

| Id | Sev. | Categoría | Qué | Ubicación |
|---|---|---|---|---|
| BUKKIT-8 | 🟠 | `memory-leak` | AnvilListener.recipeCache is unbounded and has no close/quit eviction | `AnvilListener.kt:36` |
| CFG-10 | 🟡 | `dead-code` | ResourceListener.onReload is never invoked: the runtime reload re-runs the initial-load path (defaults re-export + duplicate accumulation) | `ResourceLoaderImpl.kt:26` |
| EDITOR-12 | 🟡 | `dead-code` | Six of the seven recipe-type factory references are copy-pasted with the wrong generic type and are not registered | `RecipeTypeSpecificStateFactories.kt:15` |
| RECIPE-15 | 🟡 | `dead-code` | Grinding recipe with no addition rejects the item when it is placed in the addition slot, defeating the code that handles exactly that case | `CustomRecipeGrindingImpl.kt:55` |
| BUKKIT-17 | 🟡 | `memory-leak` | FurnaceListener.recipeCache keeps an entry per block position that is only ever removed on a successful smelt | `FurnaceListener.kt:34` |
| EDITOR-5 | 🟡 | `memory-leak` | Editor sessions are never removed: deleteSession() has no caller anywhere in the repo | `SessionManagerImpl.kt:7` |
| BUKKIT-18 | 🟡 | `performance` | Full server-wide recipe scan on every PrepareSmithingEvent | `SmithingListener.kt:66` |
| CORE-2 | 🟡 | `performance` | Backup command zips the whole resource tree synchronously on the main server thread | `MainCommand.kt:26` |
| CORE-9 | 🟡 | `performance` | Recipe tab-completion allocates the full key list on every keystroke and matches case-sensitively | `RecipesCommand.kt:41` |
| FABRIC-10 | 🟡 | `performance` | Every furnace block entity eagerly allocates a 60-element recipe-seed cache it will almost never use | `AbstractFurnaceBlockEntityMixin.java:34` |
| FABRIC-11 | 🟡 | `performance` | Recipe-priority comparator formats a debug log line on every comparison during RecipeMap.create | `RecipeMapMixin.java:57` |
| UI-6 | 🟡 | `performance` | Formula grid rebuilds 9 bundle icons and re-reads the whole ingredient collection 9 times per recomposition | `FormulaPage.kt:243` |
| CFG-8 | 🟡 | `resource-leak` | Backup retention `keep` is never applied - backups accumulate without bound although the default config promises "Keep the past 8 backups" | `DirectoryBackupDestination.kt:23` |
| CORE-5 | 🟡 | `resource-leak` | Sentry Log4J appender is attached to the global root logger and never removed; Sentry is never closed on disable | `SentryUtils.kt:54` |
| BUILD-12 | ⚪ | `dead-code` | Five unused imports in the changelog convention plugin | `build.docs.changelog.gradle.kts:1` |
| BUILD-9 | ⚪ | `dead-code` | bStats and ProtocolLib are declared as `api` dependencies, relocated and added to plugin.yml libraries, but neither is used anywhere in the source | `build.spigotlike.gradle.kts:44` |
| CFG-16 | ⚪ | `dead-code` | ConfigurationManager.load() is a no-op while parsing happens in the constructor; three declared settings files are never read | `ConfigurationManagerImpl.kt:97` |
| CORE-12 | ⚪ | `dead-code` | DataManager duplicates its DATA_PATH constant and takes an unused CustomCrafting parameter | `DataManager.kt:12` |
| EDITOR-14 | ⚪ | `performance` | runBlocking wrapper in CustomIngredientModelImpl.complete() with no suspending call inside | `IngredientModelImpl.kt:36` |
| CORE-11 | ⚪ | `resource-leak` | Properties resource stream is never closed and a missing resource throws from the static initializer | `CustomCraftingProperties.kt:16` |
| EDITOR-15 | ⚪ | `simplification` | Editor command tree is rebuilt three times and takes a dispatcher argument it never uses | `RecipesEditorCommand.kt:28` |

> **Sesgo conocido de esta pasada.** 73 de los 115 hallazgos son `bug`/`correctness` y solo 21 son
> optimización. El motor de recetas (`core/api :: core/recipe`), que es la ruta más caliente del plugin
> — corre en cada craft y en cada clic sobre una rejilla — **no produjo ni un solo hallazgo de**
> **rendimiento**, porque su lector encontró tanta rotura de correctitud que se fue tras ella. Una pasada
> dedicada a perfilado/asignación sobre esa rebanada sigue pendiente.

---

## Hallazgos

### Motor de recetas — `core/api :: core/recipe`

<details><summary>Cobertura declarada por el lector (48 ficheros)</summary>

Read in full: CraftingFormula.kt, CraftingFormulaShapedImpl.kt, CraftingFormulaShapelessImpl.kt, the whole evaluation/ package (CraftingMatrixData(.Impl), RecipeInput(.Impl), RecipeEvaluationResult, EvaluationResultImpl, IngredientData(.Impl)), the ingredient/ package (Ingredient(.Impl), the two matchers, the three consumers, the two remainders, RemainsIgnoreOptionsImpl), RecipeChoices(.Impl), CustomRecipe.kt and every CustomRecipe*Impl (Crafting, Cooking, Grinding, Repairing, Smithing, Stonecutting, Mixing), RecipeResult(.Impl), condition/ (Condition, RecipeConditions, RecipeConditionsImpl) and the RecipeConditions registry object, RecipeIndex.kt, RecipeManager.kt, RecipeManagerCommon.kt, RecipeReferenceImpl.kt, RecipeTypeImpl.kt, RecipeTypeIdResolver.kt, IngredientManagerCommon.kt, SmithingUtils.kt, RecipeItemTransmuters.kt, action/ (ResultAction, CommandResultAction), modifier/ (RecipeItemModifierImpl, TransformationImpl), process/ (ProcessGrindingDefaultImpl, ProcessRepairingCustomImpl, ProcessRepairingFixedResultImpl), procedure/ (DamageCombineImpl, EnchantingImpl, EnchantRemovalImpl, EnchantRemovalIngredientImpl, ItemRepairImpl, RepairCostImpl). I traced the full shaped and shapeless crafting path (PrepareItemCraftEvent -> CraftingMatrixData.of -> RecipeManagerCommon.evaluateRecipesOfType -> CraftingFormula*.evaluate -> RecipeResult.compute; then InventoryClickEvent -> CustomRecipeCrafting.shrink -> IngredientConsumer.consume) and the furnace/campfire path (CustomRecipeCookingImpl.WorkstationProcessing*.evaluate). To confirm reachability I also opened three files OUTSIDE my slice (core/spigotlike CraftingListener.kt, SmithingListener.kt, RecipeUtils.kt) — I report nothing whose root cause lives there. NOT covered in depth: the pure interface declarations I only skimmed (Ingredient.kt, IngredientMatcher.kt, IngredientConsumer.kt, IngredientRemainder.kt, IngredientManager.kt, RecipeType.kt, RecipeTypes.kt, ResultActions.kt, modifier/Transformation.kt, the procedure/* interfaces), the generated editor/ui bindings, and anything inside scafall (ItemStackRef.matches, ScafallItemStack.snapshot, ItemStackRef.amount semantics) whose sources are not in this repo — where a finding depended on scafall internals I could not read, I dropped it rather than guess (e.g. I could NOT prove that IngredientImpl.match ignoring stack amount leads to a negative-count ItemStack, because CraftingListener guards count<=0 first).

</details>

#### 🔴 RECIPE-1 — Trimmed crafting matrix is built with broken index arithmetic and uses maxColumn as the column offset, so every recipe smaller than the grid reads the wrong slots

**Severidad:** CRITICO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/evaluation/CraftingMatrixDataImpl.kt:42`

```
matrix = Array(width * height) {
    // Copy the values from the original array by offsetting the row and column back to the original
    this[(it / width) + minRow + (it % width) + maxColumn]
},
width,
height,
rowOffset = minRow,
columnOffset = maxColumn
```

**Impacto.** Two defects in one expression. (a) The index is an ADDITION of row and column instead of `((it/width)+minRow)*gridSize + ((it%width)+minColumn)` — no multiplication by the original row stride. (b) `minColumn` is never used; `maxColumn` is used both here and as `columnOffset` on line 47. Concrete: a 2x2 recipe placed in the top-left of a 3x3 crafting table (minRow=0,maxRow=1,minColumn=0,maxColumn=1 -> width=2,height=2) produces matrix = [slot1, slot2, slot2, slot3] instead of [slot0, slot1, slot3, slot4]. Slot0 and slot4 are never seen, slot2 (empty) appears twice. The recipe therefore fails to match, or matches a different recipe. Worse, `recipeOffset = rowOffset*gridSize + columnOffset` (line 77) then derives every `invSlot` from the wrong columnOffset, so when a craft does go through, CraftingListener writes the shrunk stacks back with `inventory.matrix = matrix` at the WRONG grid slots — items are destroyed or duplicated. This path is live: core/spigotlike/.../CraftingListener.kt:102 and CrafterListener.kt:44 both call `CraftingMatrixData.of(List)`, which is this function.

**Arreglo.** Use `this[((it / width) + minRow) * gridSize + ((it % width) + minColumn)]` and pass `columnOffset = minColumn`. Add a unit test that trims a 3x3 grid containing a 2x2 shape at each of the four corners and asserts the resulting matrix and itemIndices.

> **Verificación adversarial — confirmado.** Confirmed verbatim at core/api/.../evaluation/CraftingMatrixDataImpl.kt:40-48: `this[(it / width) + minRow + (it % width) + maxColumn]`, `columnOffset = maxColumn`, and `minColumn` (computed at :22) is never used in the trim branch. I recomputed the 2x2-at-top-left case (minRow=0,maxRow=1,minColumn=0,maxColumn=1,width=height=2): indices are 1,2,2,3 instead of 0,1,3,4 — exactly as claimed. CraftingMatrixDataImpl.kt:77 `recipeOffset = rowOffset * gridSize + columnOffset` then propagates maxColumn into itemIndices/flatItemIndices (:89), and CraftingListener.kt:88-93 writes `matrix[index] = new` into a fresh all-null array indexed by those invSlots before `inventory.matrix = matrix`, so unwritten slots are cleared — the item-loss claim holds. Call path confirmed: CraftingMatrixData.kt:67 `of()` delegates to toCraftingMatrixData(), called from CraftingListener.kt:102 and CrafterListener.kt:44. No guard upstream; I checked for index-out-of-bounds as an alternative explanation and there is none (max index <= maxRow+maxColumn <= 4 on a 3x3), so it silently reads wrong slots.

#### 🔴 RECIPE-2 — Smithing recipes report a match even when the base/template/addition items do not match their ingredients

**Severidad:** CRITICO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeSmithingImpl.kt:45`

```
val matchedTemplate = evaluateIngredient(template, input.template)
val matchedBase = evaluateIngredient(base, input.base)
val matchedAddition = evaluateIngredient(addition, input.addition)

return DefaultDataImpl(arrayOf(matchedTemplate, matchedBase, matchedAddition))
```

**Impacto.** `evaluateIngredient` returns null when `ingredient.match(inputStack)` fails (line 60-62), but none of the three results is checked before returning. The only checks performed are `validIngredient` (line 48-54) and line 35, which test EMPTINESS only, never whether the stack satisfies the ingredient. Concrete: a smithing recipe requiring template=netherite_upgrade, base=diamond_chestplate, addition=netherite_ingot. A player puts any three non-empty items in the smithing table (e.g. dirt, dirt, dirt). `evaluate` returns a non-null DefaultDataImpl, so RecipeManagerCommon.evaluateRecipesOfType returns a hit; core/spigotlike SmithingListener.kt:86-102 then computes and sets `event.result` to the recipe's output. Because all three IngredientData entries are null, `nonNullIngredients` is empty and the shrink loop consumes nothing — the player can take the output repeatedly from three worthless items. Unlimited item generation.

**Arreglo.** Fail the evaluation when a required ingredient does not match: `val matchedBase = evaluateIngredient(base, input.base) ?: return null`, and for template/addition return null when the ingredient is non-null but the match failed (keep null only for the legitimately-absent-ingredient case).

> **Verificación adversarial — confirmado.** Core claim confirmed at CustomRecipeSmithingImpl.kt:41-45: the three `evaluateIngredient` results are packed into DefaultDataImpl without any null check, and evaluateIngredient (:56-63) returns null on `ingredient.match()` failure while validIngredient (:48-54) only tests emptiness. RecipeManagerCommon.kt:146-158 `evaluateRecipesOfType` returns the first non-null data with no further validation, and SmithingListener.kt:78-102 then computes and sets `event.result`. So three arbitrary non-empty items do produce the recipe output. One part of the claimed impact is WRONG though: the shrink is not `nonNullIngredients`-based — SmithingListener.kt:176-192 `shrinkIngredient` does `data.data.bySlot(index)?.let{...} ?: ItemStack(Material.AIR)`, so null IngredientData clears the slot; the three junk items ARE consumed, so it is not unlimited repetition from the same items. It remains dirt-in/netherite-out free-output generation, so CRITICO still stands.

#### 🟠 RECIPE-3 — shrink() indexes the crafting matrix with recipeIndex, which is the ingredient-list index for shapeless recipes, not a matrix position

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeCraftingImpl.kt:39`

```
for (value in recipeEvaluationResult.data.nonNullIngredients) {
    var stack = input.matrixData.matrix[value.recipeIndex]
    stack = value.selectedIngredient.shrink(
        stack,
        count,
        value.matchedItemStackRef,
        context,
        recipeEvaluationResult
    )
    applyStacks(value.invSlot, stack)
}
```

**Impacto.** For SHAPED recipes CraftingFormulaShapedImpl.kt:64-68 builds `IngredientDataImpl(matrix.itemIndices[i], i, ...)` so recipeIndex happens to equal the matrix position and this works. For SHAPELESS, CraftingFormulaShapelessImpl.kt:58-63 builds `IngredientDataImpl(invSlot = flatItemIndices[index], ingrdRecipeIndex, ...)` where recipeIndex is the position in the INGREDIENT LIST, which is unrelated to where the item sits in the grid. Concrete: a shapeless recipe with ingredients [diamond, stick]; the player puts 2 sticks in grid slot 0 and 1 diamond in slot 1. Matching yields IngredientData(invSlot=1, recipeIndex=0 /*diamond*/) and IngredientData(invSlot=0, recipeIndex=1 /*stick*/). The loop shrinks matrix[0] (the STICK stack, in place via ItemStack.shrink) and writes it to invSlot 1, then shrinks matrix[1] (the DIAMOND) and writes it to invSlot 0. Final grid: slot0 = empty, slot1 = stick x1. The diamond is destroyed and a stick teleports. Any shapeless recipe whose grid order differs from the declared ingredient order loses or moves items.

**Arreglo.** Index by the matched item, not by the recipe slot: store the matrix index in IngredientData (or look it up via `matrixData.itemIndices.indexOf(value.invSlot)`), and use that to fetch the stack. Cleanest is to add a distinct `matrixIndex` field to IngredientData and populate it in both formula impls.

> **Verificación adversarial — confirmado.** Confirmed. CustomRecipeCraftingImpl.kt:38-48 indexes the (trimmed) matrix with `value.recipeIndex` but applies the result to `value.invSlot`. IngredientData.kt:15-20 documents invSlot as the inventory slot and recipeIndex as 'the index of the ingredient in the recipe'. CraftingFormulaShapedImpl.kt:64-69 passes `(matrix.itemIndices[i], i, ...)` so recipeIndex == trimmed-matrix position there (works by coincidence), while CraftingFormulaShapelessImpl.kt:58-63 passes `(invSlot = flatItemIndices[index], ingrdRecipeIndex, ...)` where ingrdRecipeIndex is the position in `ingredients`, unrelated to the grid. Worse than stated: matrix includes empty cells while flatItems does not, so matrix[recipeIndex] can even be an empty stack. CraftingListener.kt:88-93 confirms the wrong stack is written back to the wrong slot and unwritten slots become null (item loss).

#### 🟠 RECIPE-5 — Shaped shape trimming uses swapped width/height in its guard and the same broken row-stride arithmetic when copying

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CraftingFormulaShapedImpl.kt:124`

```
if (trim && (maxRow < width - 1 || maxColumn < height - 1 || minRow > 0 || minColumn > 0)) {
    // Trim the leading and trailing empty rows and columns
    width = maxColumn - minColumn + 1
    height = maxRow - minRow + 1
    var trimmed = Array(width * height) {
        original[(it / width) + minRow + (it % width) + minColumn]
    }
    original = trimmed
}
```

**Impacto.** Line 124 compares maxRow against `width - 1` and maxColumn against `height - 1` — the two are swapped, so for non-square shape definitions the trim is entered or skipped incorrectly. Line 132 then copies with an ADDITION instead of `((it/width)+minRow) * originalWidth + ((it%width)+minColumn)`; note also that `width` has already been overwritten on line 126, so the original row stride is not even available any more. Concrete: the interface doc for `Shape.rows` (CraftingFormula.kt:163) states rows are fixed at 3x3, so the padded form is the normal one. A 2x2 recipe declared as rows=["XX ","XX ","   "] gives original=[0,0,-1,0,0,-1,-1,-1,-1], minRow=0,maxRow=1,minColumn=0,maxColumn=1, so trimmed width=height=2 and trimmed[3] = original[1+1] = original[2] = -1. The compiled variation is [0,0,0,-1], i.e. the bottom-right cell must be EMPTY. The recipe can never be crafted as designed, and instead matches a 3-slot L shape.

**Arreglo.** Fix the guard to `maxRow < height - 1 || maxColumn < width - 1` and capture the original width in a local before reassigning, then copy with `original[((it / newWidth) + minRow) * originalWidth + ((it % newWidth) + minColumn)]`. This is the same defect as RECIPE-1 — the two copies of this loop should be extracted into one shared helper.

> **Verificación adversarial — confirmado.** Both defects confirmed at CraftingFormulaShapedImpl.kt:124-134. Line 124 really does compare `maxRow < width - 1 || maxColumn < height - 1` while width=rows.maxOf{length} (:84) and height=rows.size (:86) — swapped. Line 126-127 overwrite width/height before line 132 copies with `original[(it / width) + minRow + (it % width) + minColumn]`, an addition with no multiplication by the original row stride, and the original stride is indeed no longer available. I recomputed the quoted example rows=["XX ","XX ","   "]: original=[0,0,-1,0,0,-1,-1,-1,-1], min/maxRow=0/1, min/maxColumn=0/1, trim entered, trimmed=[0,0,0,-1] — the bottom-right cell is forced EMPTY, matching the finding exactly. trim defaults to true (:81) so this is the normal path, and the mirrored variations at :139-158 are built from the corrupted array.

#### 🟠 RECIPE-7 — A repairing recipe with no addition ingredient can never match — the elvis on the optional addition aborts the whole evaluation

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeRepairingImpl.kt:33`

```
if (addition == null && input.addition != null || addition != null && input.addition == null) {
    return null
}
val matchedAddition = addition?.match(input.addition!!)?.let { additionMatch ->
    IngredientDataImpl(1, 1, addition, additionMatch)
} ?: return null

return RepairingRecipeDataImpl(0, arrayOf(matchedBase, matchedAddition))
```

**Impacto.** `addition` is declared nullable (line 18) precisely so an anvil recipe can require only a base — e.g. a rename-only or repair-cost-only recipe driven by ProcessRepairingCustomImpl's `rename` procedure. When `addition == null`, `addition?.match(...)` evaluates to null and the `?: return null` on line 35 aborts. Line 30 has already validated the null pairing, so the elvis can only ever fire for the legitimate no-addition case or a genuine mismatch, and it cannot tell them apart. Concrete: a recipe `{base: <any_sword>, addition: null, process: {rename: {...}}}` placed in an anvil never evaluates, so the rename-only feature is entirely dead.

**Arreglo.** Split the two cases: `val matchedAddition = if (addition == null) null else addition.match(input.addition!!)?.let { IngredientDataImpl(1, 1, addition, it) } ?: return null`.

> **Verificación adversarial — confirmado.** Confirmed at CustomRecipeRepairingImpl.kt:30-37. With `addition == null` the pairing guard at :30 passes when input.addition is also null, then `addition?.match(...)` is null and the `?: return null` at :35 aborts, so a base-only repairing recipe can never match. The nullable declaration is deliberate (CustomRecipeRepairing.kt:27 declares `val addition: Ingredient?`), the rename-only flow is real (ProcessRepairing.kt:53 `rename: ProcedureRename?`, ProcessRepairingCustomImpl.kt:106-124, and AnvilListener.kt:50 passes `event.view.renameText` into the input), and the sibling CustomRecipeGrindingImpl.kt:58-60 handles the same optional-addition case correctly WITHOUT an elvis — proving the repairing version is the deviation, not the intent. I also verified AnvilListener.kt:47 uses `inventory.getItem(1)` which yields null for an empty slot, so input.addition really is null in the scenario.

#### 🟠 RECIPE-8 — Shapeless backtracking memoizes successful edges globally, pruning valid assignments and reporting no-match for craftable grids

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CraftingFormulaShapelessImpl.kt:53`

```
val edgeTo = 1 shl ingrdRecipeIndex
if (checkedEdges[edgeFrom].and(edgeTo) == edgeTo || path.contains(ingrdRecipeIndex + 1)) {
    continue
}
val matchedRef = ingredient.match(input.matrixData.flatItems[index]) ?: continue
// Found matching ingredient
...
checkedEdges[edgeFrom] = checkedEdges[edgeFrom].or(edgeTo)
```

**Impacto.** `checkedEdges[from] |= to` records a (previousIngredient -> nextIngredient) pair, but whether that pair is viable depends on the DEPTH of the search (which grid item is being matched) and on the whole prefix, not just the previous node. A node can legitimately be reached at two different depths, and the second visit is then pruned. Traced concrete counter-example with 4 ingredients and 4 items, where I0 matches {a,b}, I1 matches {b,c}, I2 matches {d}, I3 matches {a}: the search first goes root->I0(a)->I1(b), dead-ends at c, backtracks and permanently marks edge I0->I1. It later reaches root->I3(a)->I0(b) and now needs I1 for item c, but edge I0->I1 is marked checked, so I1 is skipped, the branch dies, and evaluate() returns null. The valid assignment a=I3, b=I0, c=I1, d=I2 exists. A shapeless recipe with overlapping ingredient choices (a tag plus a specific item, a common pattern) silently refuses to craft depending on which slots the player used.

**Arreglo.** The memo key must include the search depth, or the edges must be cleared on backtrack. Simplest correct fix for a 9-cell grid: clear `checkedEdges[poppedNode] = 0` when popping, or replace the whole thing with a standard bipartite matching (Hopcroft-Karp / Kuhn's) over items x ingredients, which is also faster and allocation-free with a reused int array.

> **Verificación adversarial — confirmado.** Confirmed by hand-executing CraftingFormulaShapelessImpl.kt:44-79 on the stated counter-example (I0={a,b}, I1={b,c}, I2={d}, I3={a}, items a,b,c,d). checkedEdges (:44) is never reset on backtrack — the backtrack block at :73-78 only pops the path and nulls pickedIngredients, it never clears the bit set at :65 — so the mark is global, not per-prefix. My trace: root->I0(a)->I1(b) dead-ends on c and permanently sets checkedEdges[1] bit1; the search later reaches root->I3(a)->I0(b), needs I1 for c, is skipped by the `checkedEdges[edgeFrom].and(edgeTo) == edgeTo` test at :53, exhausts every branch and falls out at :70-71 returning null, even though a=I3,b=I0,c=I1,d=I2 is a valid assignment. The guard at :19 (flatItems.size == ingredients.size) does not help. Holds as stated.

#### 🟡 RECIPE-10 — Recipe priority ordering is inverted: lowest-priority recipes are evaluated first and therefore win

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/RecipeIndex.kt:24`

```
private val recipeValueComparator: Comparator<RecipeReference<*>> = Comparator.comparing { it.value?.priority ?: 0 }
```

**Impacto.** `Comparator.comparing` sorts ASCENDING, and `byTypeBuilder.orderValuesBy(recipeValueComparator)` (lines 36, 68, 92) applies it to the per-type value list. RecipeManagerCommon.evaluateRecipesOfType (line 152-158) iterates that list and returns on the FIRST match. CustomRecipe.priority is documented at CustomRecipe.kt:31-33 as "Recipes of higher priority are checked before recipes of lower priority" — the opposite. Concrete: a server has a generic `any_planks -> stick` recipe at priority 0 and a special `oak_planks -> golden_stick` override at priority 100. Both match an oak-planks grid; the priority-0 generic recipe is evaluated first and wins, so the override never fires and the documented way to override a recipe does not work.

**Arreglo.** `Comparator.comparing<RecipeReference<*>, Int> { it.value?.priority ?: 0 }.reversed()`, and add a test asserting byType(crafting).first() is the highest-priority recipe.

#### 🟡 RECIPE-11 — Ratio-based durability combining does integer division of durability by max damage, which is always 0 or 1

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/procedure/ProcedureDamageCombineImpl.kt:23`

```
val baseDurPerc = baseDur / base.maxDamage
val additionDurPerc = additionDur / addition.maxDamage
val totalDurRepairPerc = baseDurPerc + additionDurPerc

// apply the ratio and bonus based on the max-damage of the result
val bonusAmount = result.maxDamage * bonusPercentage / 100
val scalarDur = totalDurRepairPerc * result.maxDamage + bonusAmount
```

**Impacto.** `baseDur` and `maxDamage` are both Int, so this is integer division: it yields 1 only when the item is completely undamaged and 0 in every other case. There is no `.toDouble()` anywhere in the expression. Concrete: a grindstone/anvil recipe with `combineDurabilityAsRatio = true`. Two half-worn diamond swords (maxDamage 1561, damageValue 780 each) give baseDurPerc = 781/1561 = 0 and additionDurPerc = 0, so scalarDur = bonusAmount only and `damage = 1561 - bonus`, which is greater than the current damageValue, so line 40's `if (damage < result.damageValue)` suppresses the write and the combine silently does NOTHING. With two pristine items the percentages are 1+1 = 2, scalarDur = 2*maxDamage + bonus, damage clamps to 0 — a full repair. The feature is all-or-nothing and never produces the intended ratio.

**Arreglo.** Compute in floating point: `val baseDurPerc = baseDur.toDouble() / base.maxDamage` (same for addition), then `val scalarDur = (totalDurRepairPerc * result.maxDamage).toInt() + bonusAmount`. Guard against maxDamage == 0 for non-damageable stacks.

#### 🟡 RECIPE-12 — Custom anvil repair divides the addition stack count by the BASE ingredient's required amount instead of the addition's

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/process/ProcessRepairingCustomImpl.kt:76`

```
// ItemStackRefs are allowed to be stacked items, so calculate how many can be used
val maxRepairCount = additionStack.count / (recipeEvaluationResult.data.bySlot(0)?.matchedItemStackRef?.amount ?: 1)
```

**Impacto.** CustomRecipeRepairingImpl.kt:37 builds `RepairingRecipeDataImpl(0, arrayOf(matchedBase, matchedAddition))`, and DefaultDataImpl.bySlot indexes that array directly, so `bySlot(0)` is the BASE ingredient and `bySlot(1)` is the addition. The comment and the surrounding code make clear the divisor must be the per-repair cost of the ADDITION. Concrete: a repairing recipe whose base ref amount is 1 and whose addition ref requires 4 iron ingots per repair step. A player puts a damaged tool plus 16 iron ingots in the anvil: maxRepairCount is computed as 16/1 = 16 repair steps instead of 16/4 = 4, so the tool is repaired up to four times more than the recipe allows while the anvil listener still consumes only what the addition ref specifies.

**Arreglo.** Use `recipeEvaluationResult.data.bySlot(1)?.matchedItemStackRef?.amount ?: 1`, and coerce the divisor to at least 1 to avoid a divide-by-zero on a malformed ref.

#### 🟡 RECIPE-13 — A custom ingredient remainder is discarded whenever the item also has a vanilla crafting remainder

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/ingredient/IngredientRemainderCustomImpl.kt:27`

```
val customRemainder = remainder.create()
val vanillaRemainder = mcSource.item.craftingRemainder

if (!ignore.vanilla && vanillaRemainder != null) {
    return listOf(vanillaRemainder.create().wrap())
}
```

**Impacto.** `ignore` defaults to `RemainsIgnoreOptionsImpl(vanilla = false, others = false)` (line 12), so by default the vanilla remainder short-circuits and the explicitly configured `remainder` is thrown away and never reaches IngredientConsumerConsumeImpl. This is the Custom remainder class — its entire purpose is to override the remainder. Concrete: a recipe whose ingredient is a water bucket with a configured custom remainder of, say, a cracked bucket. Water bucket has a vanilla crafting remainder (empty bucket), so with the default ignore options the player always gets the vanilla empty bucket back and the configured item is silently ignored; the recipe author has no way to tell it was dropped. `customRemainder` is also allocated on line 24 before the branch that discards it, on the per-craft path.

**Arreglo.** Return the custom remainder as the primary result and only fall back to (or additionally include) the vanilla one when configured to: build the list as `listOfNotNull(customRemainder, vanillaRemainder?.takeIf { !ignore.vanilla }?.create()?.wrap())`, or invert the branch so the custom value wins. Also move `remainder.create()` after the branch that needs it.

#### 🟡 RECIPE-14 — IngredientManagerCommon.onFinalize aliases the tracking set instead of copying it, so stale ingredients are never removed and re-registration throws

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/IngredientManagerCommon.kt:66`

```
val previouslyLoaded = this.ingredientsLoadedByCC
this.ingredientsLoadedByCC.clear()

verifyIngredientsAndLoad()

val removed = previouslyLoaded.subtract(ingredientsLoadedByCC)
```

**Impacto.** `previouslyLoaded` is the SAME MutableSet object as `ingredientsLoadedByCC`, not a snapshot — the very next line clears it, so `previouslyLoaded` is emptied too and `subtract` always returns the empty set. Compare RecipeManagerCommon.kt:124, which correctly does `recipesLoadedByCC.toSet()`. Concrete: an admin deletes an ingredient file and reloads; the ingredient stays registered in the BiMap forever and any recipe referencing it keeps resolving, so the delete appears to do nothing. Compounding this, `ingredientsAwaitingDependencies` is never cleared, and `verifyIngredientsAndLoad` is invoked both from the constructor's `onDependencyInitialized` hook (line 26-28) and from onFinalize; the second pass re-calls `registerIngredient` with the same keys, which hits `error("Ingredient with key $key already exists.")` on line 36 and aborts the remainder of the load with an IllegalStateException.

**Arreglo.** `val previouslyLoaded = this.ingredientsLoadedByCC.toSet()`, and clear `ingredientsAwaitingDependencies` at the end of verifyIngredientsAndLoad (or make registerIngredient an upsert for keys this manager already owns).

#### 🟡 RECIPE-15 — Grinding recipe with no addition rejects the item when it is placed in the addition slot, defeating the code that handles exactly that case

**Severidad:** MEDIO · **Categoría:** `dead-code` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeGrindingImpl.kt:55`

```
if (addition == null && !emptyAddition || addition != null && emptyAddition) {
    return null
}
```

**Impacto.** `emptyAddition` is computed once on line 31 from the ORIGINAL input and is never recomputed after lines 40-44 move the addition stack into the base role (`baseInvSlot = 1; baseStack = input.addition; additionStack = null`). So for a single-input grinding recipe where the player uses the lower (addition) grindstone slot, `addition == null && !emptyAddition` is true and the method returns null — even though `matchedBase` was already successfully computed on line 51. The whole `if (addition == null)` block on lines 38-45, including its explanatory comment "So lets take the addition and use it as if it were placed in the base slot", is therefore unreachable in its effect. Concrete: a grindstone recipe that strips enchantments from a sword; the player drops the sword in the bottom slot (which vanilla accepts) and nothing happens. Note the array built on line 63 for this branch is also inconsistent (`arrayOf(null, matchedBase)` stores an entry whose recipeIndex is 0 at array position 1).

**Arreglo.** Recompute the emptiness flags after the slot-swap, e.g. `val effectiveAdditionEmpty = additionStack == null || additionStack.isEmpty` and use that on line 55; and make the returned array positions agree with the recipeIndex values stored in the IngredientData entries.

#### 🟡 RECIPE-16 — RecipeResult.compute calls random() on the choices collection without an empty check, throwing inside the craft event

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/RecipeResultImpl.kt:19`

```
val pickedChoice = choices.all().random(random) // TODO: custom weighting?
val stack = pickedChoice.create()
```

**Impacto.** Kotlin's `Collection<T>.random(Random)` throws NoSuchElementException("Collection is empty.") on an empty collection. `RecipeChoicesImpl.combinedChoices` is empty when a recipe file declares an empty `stacks` list with no `tags` (or only tags that resolve to nothing, e.g. a tag key from a mod that is not installed — see RecipeChoicesImpl.kt:22-29, where a missing tag contributes zero entries and logs nothing). RecipeManagerCommon.verifyRecipesAndLoad has an explicit `// TODO: verify recipe` and performs no validation, so such a recipe loads. Concrete: a player opens a crafting table with the matching ingredients; PrepareItemCraftEvent -> CraftingListener.kt:108 -> result.compute throws NoSuchElementException on the main server thread inside the event handler, spamming the console and leaving the crafting UI in an inconsistent state on every grid change.

**Arreglo.** `val pickedChoice = choices.all().randomOrNull(random) ?: return ItemStack.EMPTY.wrap()`, and additionally reject recipes with empty result choices at load time in verifyRecipesAndLoad.

#### 🟡 RECIPE-4 — RecipeIndex.registerOrUpdateAll rebuilds byKey/byType from ONLY the new recipes, silently unregistering every previously loaded recipe

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/RecipeIndex.kt:71`

```
updatedRecipes.addAll(this.recipes)
val updatedByKey = byKey.toMutableMap()
for (recipe in recipes) {
    val existing = updatedByKey.remove(recipe.key)
    updatedRecipes.remove(existing?.value)
    updatedRecipes.add(recipe.value)

    val ref = RecipeReference.of(recipe.key, recipe.value)
    byTypeBuilder.put(recipe.value.type, ref)
    byKeyBuilder.put(recipe.key, ref)
}

return RecipeIndex(updatedRecipes, byKeyBuilder.build(), byTypeBuilder.build())
```

**Impacto.** `updatedByKey` is a dead local — it is populated from the existing map and then only ever had entries REMOVED from it; it is never fed into `byKeyBuilder` and the existing `byType` entries are never fed into `byTypeBuilder`. The returned index's lookup maps therefore contain only the recipes passed in this call. `recipes` (the plain list) is carried over, which hides the problem from `recipes().size`-style checks but not from lookups. Concrete: RecipeManagerCommon.verifyRecipesAndLoad() runs once from the constructor's `onDependencyInitialized` hook (line 51-55) and again from `onFinalize` (line 127); any third-party add-on or a later batch that calls `registerOrUpdateRecipes(listOf(oneRecipe))` wipes every other recipe out of `byKey` and `byType`, so `evaluateRecipesOfType` returns nothing and every custom recipe on the server stops working until restart, while `getRecipe(key)` returns null for them.

**Arreglo.** Seed both builders with the surviving existing entries before adding the new ones: put every entry of `updatedByKey` (after the removals) into `byKeyBuilder` and its corresponding refs into `byTypeBuilder`, then add the new refs. Note ImmutableMap.Builder throws on duplicate keys, so the removal must happen before seeding.

> **Verificación adversarial — severidad corregida a MEDIO.** Code claim confirmed at RecipeIndex.kt:64-83: `updatedByKey` (:71) is only ever read via `remove` to drop stale entries from the plain `recipes` list; neither it nor the existing `byType` multimap is fed into byKeyBuilder/byTypeBuilder (:77-79), so the returned index's lookup maps contain only the batch passed in, while `updatedRecipes` (:70) keeps everything — exactly the asymmetry described. Severity lowered: I traced every caller. `registerOrUpdateRecipes` has exactly one in-repo call site, RecipeManagerCommon.kt:143, and it always passes the full accumulated `awaitingVerificationRecipes` list, which is never cleared (grep shows adds at :106 only). So both invocations (init hook :51-55 and onFinalize :127) re-register the complete set and nothing is actually lost today; the server-wide breakage needs an incremental caller that does not yet exist in this repo, only a third party using the public RecipeManager.kt:56 API. Latent contract violation, not a live outage.

#### 🟡 RECIPE-6 — Repairing and grinding recipes never evaluate their conditions, so permission/world gates on anvil and grindstone recipes are ignored

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/CustomRecipeRepairingImpl.kt:26`

```
override fun evaluate(
    input: RecipeInput.RepairingRecipeInput,
    context: EvaluationContext,
): RecipeEvaluationResult.RepairingRecipeData? {
    val matchedBase = base.match(input.base)?.let { baseMatch ->
        IngredientDataImpl(0, 0, base, baseMatch)
    } ?: return null
```

**Impacto.** `conditions` is declared on the class (line 15) and serialized into the recipe file, but `conditions.areSatisfied(context)` is never called in this method — compare CustomRecipeCraftingImpl.kt:22, CustomRecipeCookingImpl.kt:28 and CustomRecipeStonecuttingImpl.kt:25, which all check it as the first statement. CustomRecipeGrindingImpl.kt:22-65 has the identical omission. Concrete: a server defines a repairing recipe gated on a `permission` condition so only donors can repair netherite at an anvil. Any player opens an anvil with the right items; core/spigotlike AnvilListener.kt:52 calls evaluateRecipesOfType, this method returns data without consulting the condition, and the result is produced. Every condition type (permission, world, advancement, elite-workbench, …) is a no-op for the repairing and grinding recipe types.

**Arreglo.** Add `if (!conditions.areSatisfied(context)) return null` as the first statement of both CustomRecipeRepairingImpl.evaluate and CustomRecipeGrindingImpl.evaluate. Better: hoist the check into a `CustomRecipe` default/template method so a new recipe type cannot forget it.

> **Verificación adversarial — severidad corregida a MEDIO.** Omission confirmed: `grep -rn areSatisfied` over the whole repo returns hits only in CustomRecipeCookingImpl.kt:28, CustomRecipeCraftingImpl.kt:22, CustomRecipeSmithingImpl.kt:30 and CustomRecipeStonecuttingImpl.kt:25. CustomRecipeRepairingImpl.kt:22-38 and CustomRecipeGrindingImpl.kt:22-65 never touch `conditions` despite declaring it (:15). I also checked for a central gate: RecipeManagerCommon.kt:146-158 only calls `isRecipeDisabled`, so nothing upstream enforces conditions. Severity lowered because the concrete exploit is not configurable today: the registry CustomCraftingRegistryTypes.kt:36 `recipeConditionTypes` is created empty (CustomCraftingRegistriesCommon.kt:46) and grep finds no Condition implementation anywhere in the repo, so with the Jackson type-id resolver on Condition.kt:15-17 no permission/world condition can currently be deserialized into any recipe. It is a real gap only for third-party-registered condition types.

#### 🟡 RECIPE-9 — Vacuous null check on a non-nullable array makes flatItemIndices include empty slots, desynchronising it from flatItems

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/evaluation/CraftingMatrixDataImpl.kt:88`

```
if (matrix[index] != null) {
    val ogIndex = index + recipeOffset + row * rowSkip
    indices.add(ogIndex)
    flatIndices.add(ogIndex)
} else {
    indices.add(-1)
}
```

**Impacto.** `matrix` is declared `Array<ScafallItemStack>` (line 68) — a non-nullable element type — so `matrix[index] != null` is always true and the else branch is dead. The intent (per the docs on CraftingMatrixData.flatItemIndices, "the indices of the items ... without empty slots") was clearly `!matrix[index].unwrap().isEmpty`, matching how `flatItems` is built on line 75. As written, flatItemIndices gets one entry per SLOT while flatItems gets one per NON-EMPTY item, so the two lists are misaligned as soon as the trimmed matrix contains a hole. Concrete: a shapeless 3-ingredient recipe with items at grid slots 0, 2 and 4 of a 3x3 table trims to a 3x2 region with empties at trimmed positions 1, 3, 5. CraftingFormulaShapelessImpl.kt:59 then reads `flatItemIndices[1]` = slot 1 (empty) for the item that actually lives in slot 2, so the resulting IngredientData carries the wrong `invSlot` and CustomRecipeCraftingImpl.shrink writes the consumed stack into the wrong grid slot.

**Arreglo.** Use `if (!matrix[index].unwrap().isEmpty)` so the else branch (indices.add(-1)) is actually reachable and flatIndices only receives non-empty slots.

#### ⚪ RECIPE-17 — Grinding XP yield uses the global Random instead of the seeded Random passed in, so the reward re-rolls on every menu update

**Severidad:** BAJO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/process/ProcessGrindingDefaultImpl.kt:98`

```
if (yield > 0) {
    val reduced = ceil(yield / 2.0).toInt()
    yield = reduced + Random.nextInt(reduced)
} else {
```

**Impacto.** `compute` receives `random: Random` (line 38) whose whole purpose, per the RecipeResult.compute docs (RecipeResult.kt:74-80), is to make the output deterministic for a stored seed so "players cannot reroll the result". `Random.nextInt(reduced)` resolves to the kotlin.random.Random companion (Random.Default), not the parameter — the parameter is unused in this method. Concrete: a player puts an enchanted item in a grindstone and taps an item in and out of the second slot; each recomputation produces a different XP yield, letting the player fish for the maximum before collecting.

**Arreglo.** Use the injected instance: `yield = reduced + random.nextInt(reduced)`.

#### ⚪ RECIPE-18 — RecipeManagerCommon.index is a non-volatile var swapped from the resource-loading path and read from the main-thread craft path

**Severidad:** BAJO · **Categoría:** `concurrency` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/recipe/RecipeManagerCommon.kt:30`

```
private var index: RecipeIndex = RecipeIndex(emptyList())
```

**Impacto.** The RecipeIndex object itself is immutable by design (its KDoc at RecipeIndex.kt:16-18 says "This index is immutable ... That way, the index is thread-safe"), but the reference holding it is a plain non-volatile field. `registerOrUpdateRecipes`/`removeRecipes` (lines 182, 186) assign it, and `onReload` is documented on line 114 as "This should run on a separate thread, async to the main thread". `evaluateRecipesOfType` (line 151) reads it on the main server thread on every PrepareItemCraftEvent. Without volatile there is no happens-before edge, so the main thread may keep observing the stale index indefinitely after a reload, or (on a weak memory model) observe the new reference with partially-visible contents. Note `RecipeIndex.get` uses `synchronized(this)` on the index instance, which does not help since the race is on the field, not the object.

**Arreglo.** Make it `@Volatile private var index` (or an AtomicReference<RecipeIndex>), and drop the misleading `synchronized(this)` inside RecipeIndex.get.


### Configuración y carga de recursos — `core/api :: core/configuration + core/resource`

<details><summary>Cobertura declarada por el lector (35 ficheros)</summary>

Read all 35 files of the slice in full: core/api/.../core/configuration/** (ConfigurationManager(+Impl), ErrorTrackingSettings, cli/, gui/, mechanics/, resources/: SourceSettings, ResourceSettings(+Impl), BackupSettings(+Impl), DirectorySourceSettingsImpl, SQLSourceSettingsImpl, FilterSettingsImpl, DirectoryBackupDestinationSettingsImpl) and core/api/.../core/resource/** (ResourceManager(+Common), ResourceLoader(+Impl), Source, DirectorySource, DestinationFilter, DataType, LoadedObject, ResourceListener, BackupManager(+Impl), BackupDestination, DirectoryBackupDestination, database/: SQLSource, DataTables, DatabaseCache, DatabaseInfo, JsonValueTable). To confirm call chains I also opened, outside the slice: core/api/.../core/util/ResourceUtils.kt and IdentifierExt.kt, core/api/.../core/recipe/RecipeManagerCommon.kt and IngredientManagerCommon.kt, core/common/.../commands/RecipesCommand.kt and MainCommand.kt, core/spigotlike/CustomCraftingServerSpigotLike.kt, core/fabric/CustomCraftingServerFabric.kt, editor/common/.../SessionStateImpl.kt, and the shipped default core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf. Not verified: the scafall `Key.key()` validation rules and HoconMapper's FAIL_ON_UNKNOWN_PROPERTIES defaults (external dependency, sources not in the repo) - so I did not claim anything that depends on them (in particular I did not report unknown-key handling for HOCON, and CFG-12's traversal claim is scoped to what the code itself does with key.value). No files were modified.

</details>

#### 🟠 CFG-1 — A single extension-less file in the recipes directory aborts loading of every recipe (take(-1) throws outside the try/catch)

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/DirectorySource.kt:134`

```
return Key.key(namespace, pathString.take(pathString.lastIndexOf('.')))   // called at line 60: `val key = relative.toKey(Key.CUSTOMCRAFTING_NAMESPACE)` - BEFORE the `try {` on line 62
```

**Impacto.** Files.walkFileTree visits every regular file under <resources>/<source>/recipes. For a file with no dot in its relative path (README, a leftover `backup`, a `.gitignore` in a subdir), lastIndexOf('.') returns -1 and kotlin's String.take(-1) throws IllegalArgumentException ("Requested character count -1 is less than zero"). The call is on line 60, outside the try/catch on 62-70 that was added to make one bad file non-fatal, and readFiles (line 109) only catches IOException, so the exception escapes DirectorySource.load -> RecipeManagerCommon.onInitialLoad -> ResourceLoaderImpl.loadResources. Concrete: an admin drops a `README` file into plugins/CustomCrafting/resources/custom/recipes/ -> on startup (and on /recipes reload) zero custom recipes load from that source, and the sources after it in the list never load either.

**Arreglo.** Compute the extension index once and guard it: `val dot = pathString.lastIndexOf('.'); val value = if (dot > 0) pathString.substring(0, dot) else pathString` (and skip files whose extension is not `.conf` entirely), then move the key construction inside the existing try/catch so one bad file only logs an error.

> **Verificación adversarial — confirmado.** Confirmed verbatim. DirectorySource.kt:128-135 `Path.toKey` ends with `Key.key(namespace, pathString.take(pathString.lastIndexOf('.')))` — no dot-presence guard, and kotlin's String.take(-1) throws IllegalArgumentException. It is called at DirectorySource.kt:60, two lines ABOVE the `try {` at line 62 (the try/catch at 62-70 only wraps the Jackson readValue/accept). The visitor is invoked from NamespaceFileVisitor.visitFile (line 116-118) via Files.walkFileTree, and readFiles (line 106-111) catches only IOException, so an IllegalArgumentException escapes load(). RecipeManagerCommon.kt:102-108 calls `resourceLoader.sources.forEach { dest -> dest.load(...) }` with no try/catch, and ResourceLoaderImpl.kt:36-39 calls onInitialLoad unguarded, so the throw aborts the remaining sources and the whole load pass. No upstream filtering of extension-less files exists (SimpleFileVisitor visits every regular file). Holds at ALTO.

#### 🟠 CFG-2 — ResourceLoaderImpl.save discards Result.failure from every destination - a failed save is reported to the user as success

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/ResourceLoaderImpl.kt:52`

```
val result = destination.save(type, key, value)
if (result.isSuccess && result.getOrNull() == true) {
    if (!destination.settings.propagateSavedResources) {
        break
    }
}
```

**Impacto.** The Result is only inspected for the propagate/break decision; `result.isFailure` is never logged or propagated (save() returns Unit). DirectorySource.save returns Result.failure(e) on IOException (line 86-88) and Result.failure(Exception("Could not create file ...")) when the file cannot be created (line 90). Concrete: the resources directory is read-only / the disk is full -> a player finishes a recipe in the editor, editor/common/.../SessionStateImpl.kt:13-14 calls save() and then unconditionally logs "Saved $key", the GUI reports success, and nothing is ever written anywhere and nothing appears in the console. Same silent loss when every source is rejected by its filter (see CFG-3): the loop simply falls through.

**Arreglo.** Log (customCrafting.logger.error) on result.isFailure with the key and the destination, track whether any destination accepted the value, and change ResourceLoader.save to return a Result/Boolean so the editor can tell the player the recipe was not saved.

> **Verificación adversarial — confirmado.** Confirmed. ResourceLoaderImpl.kt:47-59: `save` is declared `fun <T:Any> save(...)` returning Unit (ResourceLoader.kt:30), and line 52-57 inspects `result` only via `if (result.isSuccess && result.getOrNull() == true)` to decide the `break`; `result.isFailure`/`exceptionOrNull()` is never logged or rethrown, and if no source accepts the key the loop just falls through. DirectorySource.kt:86-90 genuinely returns Result.failure on IOException and on failure to create the file. The caller editor/common/.../SessionStateImpl.kt:11-15 calls resourceLoader.save(...) and then unconditionally logs "Saved $key" on the next line, so a failed write is reported as success with nothing in the console. Same pattern in delete (line 61-71).

#### 🟠 CFG-3 — FilterSettings `paths` entries never match: stripping the prefix leaves a leading '/' so the direct-child check always fails

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/DestinationFilter.kt:36`

```
settings.paths.any {
    key.value.startsWith(it) &&
            key.value.replace(it, "").lastIndexOf("/") == -1
}
```

**Impacto.** For the form documented in SourceSettings.FilterSettings.FilterEntry.paths (`<namespace>:<**This path here**>/recipe`), paths=["mydir"] and key `customcrafting:mydir/recipe1`: startsWith is true, replace gives "/recipe1", lastIndexOf("/")==0 != -1 -> no match. The entry only ever matches if the admin writes the trailing slash ("mydir/"), which the docs do not say. Concrete: a source configured with `filter { includes { paths = ["mydir"] } }` accepts nothing - IncludesFilter.matches returns false for every key, DestinationFilter.accepts returns false, and ResourceLoaderImpl.save skips that source for all recipes; combined with CFG-2 the recipe is dropped with no message at all. The same expression also uses replace() (all occurrences) instead of removePrefix(), so a key like "a/b/a" with paths=["a"] is mangled.

**Arreglo.** Use `key.value.removePrefix(if (it.endsWith('/')) it else "$it/")` and require that the remainder contains no '/', i.e. `val rest = key.value.removePrefix(prefix); key.value.startsWith(prefix) && !rest.contains('/')`, with the empty path handled as the root case.

> **Verificación adversarial — confirmado.** Confirmed. DestinationFilter.kt:34-41 is exactly `settings.paths.any { key.value.startsWith(it) && key.value.replace(it, "").lastIndexOf("/") == -1 }`. For paths=["mydir"] and key.value="mydir/recipe1" the replace yields "/recipe1" whose lastIndexOf("/")==0, so the entry never matches — only a trailing-slash form ("mydir/") or the empty string (root) works, while SourceSettings.kt:202-210 documents the form `<namespace>:<**This path here**>/recipe` with no trailing slash. No normalization exists anywhere: FilterSettingsImpl.kt:15 stores `paths` raw and DestinationFilter is its only consumer (grep over the repo shows no other use of `paths`). The replace()-instead-of-removePrefix() mangling claim is also literally what line 37 does. accepts() (line 15-23) then returns false and ResourceLoaderImpl.kt:49-51 skips the source.

#### 🟠 CFG-4 — SQLSource splits a key with no '/' into dir==name, corrupting root-level resources on a save/load round trip

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/database/SQLSource.kt:68`

```
val dir = key.value.substringBeforeLast("/")
val name = key.value.substringAfterLast("/")
```

**Impacto.** Kotlin's substringBeforeLast/substringAfterLast default missingDelimiterValue to the whole string. A root-level key `customcrafting:my_recipe` (fully supported by DirectorySource, which stores it as recipes/my_recipe.conf) is written to SQL as dir="my_recipe", name="my_recipe". On the next load, line 53 rebuilds `Key.key(CUSTOMCRAFTING_NAMESPACE, "$dir/$key")` = `customcrafting:my_recipe/my_recipe`. Concrete: with an SQL source configured, a recipe created without a directory in the editor comes back after a restart under a different key - the old key disappears from the recipe index and any recipe book / command referencing it breaks. delete() (line 85-86) has the identical split, so deleting that recipe by its real key targets the wrong row and removes nothing.

**Arreglo.** Use an explicit missing-delimiter value: `val dir = key.value.substringBeforeLast('/', "")` and `val name = key.value.substringAfterLast('/')`, and on load only prepend the dir when it is not blank (`if (dir.isEmpty()) key else "$dir/$key"`).

> **Verificación adversarial — confirmado.** Confirmed and if anything understated. SQLSource.kt:68-69 uses substringBeforeLast("/")/substringAfterLast("/") with the default missingDelimiterValue, so a key with no '/' yields dir==name. Load at SQLSource.kt:43-53 rebuilds `Key.key(Key.CUSTOMCRAFTING_NAMESPACE, "$dir/$key")`; the only normalization (lines 46-51) strips a trailing/leading '/', which does not help here, so `my_recipe` returns as `my_recipe/my_recipe`. Root-level keys are not hypothetical: RecipeEditorCLI.kt:23,33-39 builds the key from `StringArgumentType.word()` (which cannot contain '/') via `Key.customCrafting(recipeName)`, so every CLI-saved recipe is root-level. delete() at SQLSource.kt:85-90 repeats the identical split, so the deleteWhere targets dir=name and misses the intended row.

#### 🟠 CFG-5 — SQLSource.save always INSERTs, so re-saving an existing recipe throws a primary-key violation instead of returning Result.failure

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/database/SQLSource.kt:71`

```
transaction(getOrCreateDBConnection()) {
    table.insert {
        it[table.dir] = dir
        it[table.name] = name
        it[config] = value
    }
}
return Result.success(true)
```

**Impacto.** JsonValueTable declares `PrimaryKey(dir, name)` (JsonValueTable.kt:16), and there is no update/upsert path. Concrete: a player opens an existing recipe in the editor and saves it again -> Exposed executes INSERT -> the JDBC driver raises a unique-constraint SQLException, which is not caught anywhere (Source.save is documented to return "an exception when an error occurred" as a Result, and ResourceLoaderImpl.save has no try/catch) -> the exception unwinds through the GUI click handler, the recipe is never updated in the database, and the loop over the remaining sources is aborted so lower-priority destinations are skipped too. The declared contract `Result<Boolean>` is never honoured for this path.

**Arreglo.** Use Exposed's `upsert`/`replace` on (dir, name), or update-then-insert, and wrap the whole transaction in `runCatching { ... }` so failures come back as Result.failure instead of propagating.

> **Verificación adversarial — confirmado.** Confirmed. SQLSource.kt:70-78 is `transaction(getOrCreateDBConnection()) { table.insert { ... } }` followed by an unconditional `return Result.success(true)`; only `org.jetbrains.exposed.v1.jdbc.insert` is imported (line 17) — there is no upsert/update/replace path anywhere in the file. JsonValueTable.kt:16 declares `override val primaryKey = PrimaryKey(dir, name)`, so a second save of the same key violates the constraint and Exposed raises an ExposedSQLException, which is caught neither in SQLSource.save nor in ResourceLoaderImpl.kt:47-59 (no try/catch), so it unwinds through saveRecipe (SessionStateImpl.kt:13) into the GUI/command handler and aborts the loop over the remaining sources. The declared `Result<Boolean>` contract (Source.kt:19-24) is not honoured for this path.

#### 🟡 CFG-10 — ResourceListener.onReload is never invoked: the runtime reload re-runs the initial-load path (defaults re-export + duplicate accumulation)

**Severidad:** MEDIO · **Categoría:** `dead-code` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/ResourceLoaderImpl.kt:26`

```
override fun loadResources() {
    ... for (listener in listeners) { listener.onPrepare(this) }
    ... for (listener in listeners) { listener.onInitialLoad(this) }
    ... for (listener in listeners) { listener.onFinalize(this) }
```

**Impacto.** loadResources() is the only entry point on ResourceLoader and always calls onPrepare/onInitialLoad/onFinalize; a repo-wide grep for `onReload` finds only the interface declaration and the two empty overrides, so that callback is dead. `/recipes reload` (RecipesCommand.kt:72-75) calls loadResources() again: onPrepare re-runs RecipeManagerCommon.exportDefaults (re-copying every default recipe out of the jar over the admin's files with overwrite=true) and onInitialLoad appends every recipe again to RecipeManagerCommon.awaitingVerificationRecipes, a list that is never cleared - so after N reloads the verification loop and registerOrUpdateRecipes process N copies of every recipe.

**Arreglo.** Give ResourceLoader a distinct `reloadResources()` (or a flag on loadResources) that dispatches onReload instead of onPrepare/onInitialLoad, and have the command call it; keep onPrepare for first startup only.

#### 🟡 CFG-11 — DirectorySource with the documented "optional" path omitted resolves to a doubled path on Spigot/Paper

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/DirectorySource.kt:26`

```
val path: String = settings.path ?: resourceLoaderImpl.directory.path
...
directory = if (!resolvedPath.isAbsolute) {
    File(resourceLoaderImpl.directory, resolvedPath.pathString)
} else { File(path) }
```

**Impacto.** SourceSettings.DirectorySourceSettings.path is documented as "An optional path to the resource directory" and is typed String?. When it is omitted, `path` becomes the resource loader's own directory *path string*, which on Spigot/Paper is relative (ResourceManagerCommon builds File(plugin.dataFolder, "resources") and Bukkit's dataFolder is the relative `plugins/<name>`), so isAbsolute is false and the directory becomes plugins/CustomCrafting/resources/plugins/CustomCrafting/resources. Concrete: an admin writes `{ type = directory }` with no path -> recipes are read from and written into that nonsense nested folder (created by assureDir/mkdirs) and none of his existing recipes load. On Fabric the config dir is absolute so the same config behaves differently.

**Arreglo.** Handle the null case separately: `directory = settings.path?.let { val p = Paths.get(it); if (p.isAbsolute) File(it) else File(resourceLoaderImpl.directory, p.pathString) } ?: resourceLoaderImpl.directory`.

#### 🟡 CFG-12 — Resource key is concatenated into a file path with no traversal check, so a key containing '..' writes outside the resources directory

**Severidad:** MEDIO · **Categoría:** `security` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/DirectorySource.kt:77`

```
val destFile = File(type.resourceSubDir(directory), "${key.value}.conf")
```

**Impacto.** key.value is user-supplied (the editor's saveAs, editor/common/.../SessionStateImpl.kt:29 -> ResourceLoader.save) and is inserted verbatim into the path; '.' and '/' are legal characters in a resource key value, so `customcrafting:../../../../plugins/SomePlugin/config` resolves outside <resources>/recipes. The code then does `destFile.getParentFile().mkdirs()` (line 79) and writes the serialized recipe over whatever is there. delete() (line 94) builds the same path, so the same key can delete an arbitrary *.conf outside the resource tree. It needs a player who may use the recipe editor, but it escalates "can create recipes" into "can overwrite arbitrary files the server process can write".

**Arreglo.** Normalize and verify containment before writing: `val root = type.resourceSubDir(directory).toPath().toAbsolutePath().normalize(); val dest = root.resolve("${key.value}.conf").normalize(); if (!dest.startsWith(root)) return Result.failure(...)` - and reject '..' segments in key values when a recipe is created.

#### 🟡 CFG-13 — DatabaseCache.connections and DataTables.tables are unsynchronized global HashMaps touched from both the async reload and main-thread saves

**Severidad:** MEDIO · **Categoría:** `concurrency` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/database/SQLSource.kt:30`

```
var database = DatabaseCache.connections[databaseInfo]
if (database == null) {
    database = Database.connect(connector.jdbcUrl, connector.driver, user = connector.user, password = connector.password)
    DatabaseCache.connections[databaseInfo] = database
}
```

**Impacto.** DatabaseCache.connections is a plain `mutableMapOf()` (DatabaseCache.kt:10) and DataTables.tables a plain `HashMap()` (DataTables.kt:7), both process-wide singletons with no locking. `/recipes reload` runs loadResources on a scheduler async thread (RecipesCommand.kt:72), which reaches SQLSource.load -> getOrCreateDBConnection/DataTables.getTable, while an editor save on the server thread reaches SQLSource.save -> the same two maps. Concrete: a player saves a recipe in the editor while another admin runs /recipes reload with an SQL source configured -> concurrent put during a HashMap resize can lose the entry (a second Database.connect and a second connection pool for the same URL) or corrupt the table, and DataTables.getTable's unchecked `as JsonValueTable<T>?` hides any resulting type mismatch.

**Arreglo.** Use ConcurrentHashMap plus computeIfAbsent (or java.util.Collections.synchronizedMap with a single getOrPut under a lock) for both caches, and close/evict the Database entries when the plugin unloads.

#### 🟡 CFG-14 — The shipped default resources.conf documents SQL source keys that the parser does not accept

**Severidad:** MEDIO · **Categoría:** `config` · **Ubicación:** `core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf:27`

```
//  {
//    type = sql
//    host = ""
//    schema = ""
//    username = ""
//    password = ""
//  },
```

**Impacto.** SQLSourceSettingsImpl requires a nested `connection` object with its own `type` discriminator (h2/mariadb/mysql/oracle/postgresql/mssqlserver/sqlite) plus host/database/user/password inside it; `host`, `schema` and `username` are not properties of any settings class. Concrete: an admin uncomments this block as instructed -> Jackson cannot bind the sql subtype (missing `connection`, unknown properties) and ConfigurationManagerImpl's second init block (line 85, `configMapper.readValue<ResourceSettings>(resourcesSettingsFile)`) throws inside the constructor, so the whole plugin fails to initialize instead of just skipping the SQL source.

**Arreglo.** Replace the commented example with the shape the code actually parses (`{ type = sql, connection { type = mysql, host = "", database = "", user = "", password = "" }, overwriteExisting = false, propagateSavedResources = false }`), and wrap the readValue in a try/catch that logs the offending file and falls back to the built-in defaults.

#### 🟡 CFG-6 — SQLSource.delete always returns Result.success(false); the transaction's return value is thrown away

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/database/SQLSource.kt:93`

```
if (removed > 0) {
    return@transaction Result.success(true)
}
}
return Result.success(false)
```

**Impacto.** `return@transaction` only returns from the transaction lambda, whose value is discarded at line 88, so the function unconditionally falls through to `Result.success(false)`. Concrete: deleting a recipe that exists in the SQL source really removes the row but reports "not deleted"; any caller that branches on the Result (the TODO at ResourceLoaderImpl.kt:68 is exactly such a branch waiting to be implemented) will conclude the resource still exists and, e.g., keep it in the index or refuse to propagate the deletion.

**Arreglo.** Capture the transaction result: `val removed = transaction(getOrCreateDBConnection()) { table.deleteWhere { ... } }; return Result.success(removed > 0)`.

#### 🟡 CFG-7 — `overwriteExisting` is parsed and documented in the shipped config but is never read by any code - the setting does nothing

**Severidad:** MEDIO · **Categoría:** `config` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/configuration/resources/SourceSettings.kt:34`

```
/**
 * Whether to overwrite existing resources by resources from this destination.
 */
val overwriteExisting: Boolean
```

**Impacto.** A repo-wide grep for `overwriteExisting` finds only the interface, the two settings impls, their toString(), and the two occurrences in the shipped default resources.conf (lines 10 and 24, the latter with the comment "resources loaded from this destination override existing resources with the same path"). Neither ResourceLoaderImpl, DirectorySource, SQLSource nor RecipeManagerCommon consults it; RecipeManagerCommon.onInitialLoad blindly appends every loaded object and registerOrUpdateRecipes/index.registerOrUpdateAll lets the last source win. Concrete: an admin adds a third source after `custom` with `overwriteExisting = false` expecting his hand-written recipes to stay authoritative; the later source silently overrides them anyway, and nothing warns him.

**Arreglo.** Either honour the flag when merging loaded objects (skip a LoadedObject whose key is already present when the source has overwriteExisting = false) or remove the option from the interface and the default config until it is implemented.

#### 🟡 CFG-8 — Backup retention `keep` is never applied - backups accumulate without bound although the default config promises "Keep the past 8 backups"

**Severidad:** MEDIO · **Categoría:** `resource-leak` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/DirectoryBackupDestination.kt:23`

```
override fun backup(): Result<File> {
    val date = LocalDateTime.now()
    val backupName = dateFormat.format(date)   // ... settings.keep is never referenced anywhere in this file
```

**Impacto.** BackupSettings.BackupDestinationSettings.keep (BackupSettings.kt:32, "How many past backups to keep") is parsed into DirectoryBackupDestinationSettingsImpl and then never read; a grep for `.keep` in the whole repo only hits IngredientConsumers.keep. The shipped default resources.conf line 41 says `keep = 8 // Keep the past 8 backups`. Concrete: every `/cc backup` (MainCommand.kt:26) writes a new timestamped zip of the entire resources tree into <resources>/backup and nothing is ever pruned, so an admin who backs up before each edit fills the disk while believing only 8 backups are retained.

**Arreglo.** After a successful backup, list the destination directory, sort the entries by the parsed `dateFormat` timestamp and delete everything beyond `settings.keep` (guarding keep <= 0 as "unlimited").

#### 🟡 CFG-9 — BackupManagerImpl throws away each destination's Result, so a failed backup is silently reported as done

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/resource/BackupManagerImpl.kt:13`

```
for (destination in destinations) {
    destination.backup()
}
```

**Impacto.** DirectoryBackupDestination.backup returns Result.failure when the backup directory cannot be created (lines 28 and 54); that value is dropped here and never logged. Worse, the IO inside backup() is not wrapped: ZipOutputStream/putNextEntry (line 43) throws ZipException("duplicate entry") if two DirectorySource entries overlap, and copyRecursively/outputStream throw IOException on a permission error - those escape createBackup() and abort the remaining destinations. Concrete: an admin runs `/cc backup` on a server where the plugin folder is not writable; the command replies "Creating backup..." (MainCommand.kt:29) and the console stays empty, so he proceeds with a destructive edit believing a backup exists.

**Arreglo.** Inspect the Result (`result.onFailure { logger.error(...) }`), wrap backup() bodies in runCatching so IO exceptions become Result.failure, and make createBackup report overall success to its caller/the command.

#### ⚪ CFG-15 — H2/SQLite jdbcUrl treats a Windows absolute path as relative and mixes String with Path

**Severidad:** BAJO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/configuration/resources/SourceSettings.kt:100`

```
val finalPath = if (path.startsWith("/")) {
    path
} else {
    Path(CustomCraftingProvider.get().server!!.resourceManager.resourceLoader.directory.path, path)
}
return "jdbc:h2:$finalPath"
```

**Impacto.** Absoluteness is decided by a leading '/', so on Windows a configured `path = "C:/data/recipes"` (or "D:\\db\\cc") is classified as relative and joined under the resources directory, producing jdbc:h2:plugins\CustomCrafting\resources\C:\data\recipes - the database is created in the wrong place or the connect fails. The branches also return different types (String vs Path), so the URL is built from Path.toString() with platform separators in one branch and the raw string in the other. The same code is duplicated verbatim for SQLite (lines 165-173), and both dereference `server!!`, which NPEs if the URL is requested before the server object exists.

**Arreglo.** Use `Paths.get(path).isAbsolute` (or File(path).isAbsolute) for the test, build the value through a single `Path` and call `.toAbsolutePath().normalize().toString().replace('\\','/')`, and factor the shared logic into one helper used by both H2 and SQLite.

#### ⚪ CFG-16 — ConfigurationManager.load() is a no-op while parsing happens in the constructor; three declared settings files are never read

**Severidad:** BAJO · **Categoría:** `dead-code` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/configuration/ConfigurationManagerImpl.kt:97`

```
override fun load() {
    customCrafting.logger.info("Loading configurations...")

}
```

**Impacto.** The real work is done in the second init block (line 82-86), so load() only prints a misleading "Loading configurations..." line and there is no way to re-read config at runtime (ResourceLoaderImpl also captures settings.sources once at construction, line 17). gameMechanicSettingsFile, guiSettingsFile and cliSettingsFile (lines 38-40) are constructed, never exported by saveDefaults() and never parsed, while their getters are TODO() - any caller of guiSettings/cliSettings/gameMechanicSettings gets NotImplementedError rather than a default. ErrorTrackingSettings (configuration/ErrorTrackingSettings.kt) is referenced nowhere in the repo at all.

**Arreglo.** Move the saveDefaults()+readValue work into load() (called by the bootstrap), have it log and fall back to defaults on a parse error, and drop the unused file fields/interface until the corresponding settings are implemented.


### Núcleo, registro y arranque — `core/api` (resto) + `core/common`

<details><summary>Cobertura declarada por el lector (31 ficheros)</summary>

Read in full: core/api package root (CustomCraftingProvider.kt, CustomCraftingBoostrap.kt, InternalBootstrap.kt) and core/api/.../core/{registry,data,factories,server,util,commands,exceptions}/** (13 files), plus all 7 files of core/common/src (CustomCraftingCommon.kt, sentry/SentryUtils.kt, util/CustomCraftingProperties.kt, commands/{CCCommands,MainCommand,RecipesCommand}.kt, resources/vals.properties). To prove or kill the findings I also opened, outside my slice: resource/{ResourceManagerCommon,ResourceLoaderImpl,BackupManagerImpl,DirectoryBackupDestination}.kt, recipe/{RecipeManager,RecipeManagerCommon,RecipeIndex,RecipeItemTransmuters}.kt, recipe/condition/*, recipe/action/ResultAction.kt, recipe/modifier/Transformation.kt, configuration/ConfigurationManagerImpl.kt, the three platform bootstraps (SpigotLoaderPlugin, CustomCraftingSpigot/Paper, CustomCraftingFabricMod), CustomCraftingServerSpigotLike.kt, spigotlike/recipes/CraftingListener.kt (error-logging/PII check) and core/common/build.gradle.kts. What I could NOT verify: scafall itself is an external dependency with no sources in the repo and no jar reachable on this machine, so the exact semantics of RegistrySimple (thread-safety), registerTypeRegistry/RegistryKeyTypeIdResolver, and ScafallProvider.scheduler.async are inferred from their call sites only; I lowered CORE-8 accordingly and did not report anything resting solely on scafall internals (e.g. whether the root registry is safe to mutate concurrently). I also did not chase the Minecraft-side behaviour of Ingredient.of(HolderSet.direct(emptyList())) in IngredientUtils.toMc() for a zero-choice ingredient — plausible crash, but I could not read the MC source to confirm, so it is omitted.

</details>

#### 🟠 CORE-1 — /cc backup has no permission check at all: any player can trigger a full recursive backup of the plugin data directory

**Severidad:** ALTO · **Categoría:** `security` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/commands/MainCommand.kt:15`

```
dispatcher.register(Commands.literal(it)
    .then(Commands.literal("info").executes { ... })
    .then(Commands.literal("backup").executes { ctx ->
        val customCrafting = CustomCraftingProvider.get()
        customCrafting.logger.info("Create backup")
        customCrafting.server!!.resourceManager.backupManager.createBackup()

-- no .requires { ... } anywhere on this tree, unlike RecipesCommand.kt:26 which does
   Commands.literal(alias).requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
```

**Impacto.** On Fabric, CCCommands.registerCommands(dispatcher) is called from CustomCraftingFabricMod.kt:116 via CommandRegistrationCallback with no requires predicate, so the node defaults to permission level 0. Any non-op player types `/cc backup` (or `/customcrafting backup`) and reaches BackupManagerImpl.createBackup() -> DirectoryBackupDestination.backup() (line 33-49), which walks every DirectorySource recursively and writes a full ZIP of the recipe/config tree, on the main server thread. Spamming the command in a macro/autoclicker freezes the server tick loop and fills the disk with timestamped backups (no retention/pruning anywhere). The sibling command file gates the far less dangerous /recipes reload behind COMMANDS_GAMEMASTER, so this is an omission, not a design choice.

**Arreglo.** Add the same gate used by RecipesCommand to the `cc`/`customcrafting` root, and ideally a stricter one on `backup`: Commands.literal(it).requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) } before the .then(...) chain.

> **Verificación adversarial — severidad corregida a ALTO.** Holds. MainCommand.kt:13-33 re-read verbatim: `dispatcher.register(Commands.literal(it).then(literal("info")...).then(literal("backup")...))` with NO `.requires {}` on the root or on any child node, while RecipesCommand.kt:26 does have `.requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }` — so the omission is real and asymmetric. Registration reaches everyone on both platforms: core/fabric/.../CustomCraftingFabricMod.kt:69-74 registers via CommandRegistrationCallback (only an `env.includeDedicated` check, not a permission predicate) and core/spigotlike/.../CustomCraftingServerSpigotLike.kt:33-35 injects into `minecraftServer.commands.dispatcher` directly, bypassing Bukkit's permission map. A Brigadier node with no requirement is permission level 0. The work is real, not a no-op: the shipped default config core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf:36-45 defines a `directory` backup destination (compress=true, keep=8), so BackupManagerImpl.kt:11-15 -> DirectoryBackupDestination.kt:39-66 zips every DirectorySource tree. The `keep = 8` retention in the config is never read anywhere in DirectoryBackupDestination.kt, confirming no pruning, so repeated invocation grows unbounded. Severity corrected to ALTO: the impact is resource-exhaustion/DoS on an admin utility, with no confidentiality, integrity or privilege-escalation component — CRITICO overstates it.

#### 🟠 CORE-3 — /recipes reload re-runs the initial-load path and blows up on duplicate recipe keys, while telling the player it succeeded

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/commands/RecipesCommand.kt:72`

```
private fun reload(customCrafting: CustomCrafting): Int {
    ScafallProvider.get().scheduler.async(customCrafting) {
        customCrafting.server!!.resourceManager.resourceLoader.loadResources()
    }
    return SUCCESS_RESULT
}
```

**Impacto.** loadResources() (ResourceLoaderImpl.kt:26-44) unconditionally runs onPrepare -> onInitialLoad -> onFinalize; the dedicated onReload hook is never called. onInitialLoad appends every loaded recipe to RecipeManagerCommon.awaitingVerificationRecipes (RecipeManagerCommon.kt:106) and that list is NEVER cleared (grep: only add/read, no clear). Startup already filled it, so the FIRST `/recipes reload` makes the list contain each key twice; onFinalize -> verifyRecipesAndLoad -> registerOrUpdateRecipes -> RecipeIndex.registerOrUpdateAll feeds those duplicates into a Guava ImmutableMap.builder (RecipeIndex.kt:66/80), whose build() throws IllegalArgumentException("Multiple entries with same key"). The exception is thrown on the async task thread, the command already returned SUCCESS_RESULT, and the player is shown nothing: reload is silently broken and the recipe index is left untouched. There is also no re-entrancy guard, so two operators reloading at once run onInitialLoad/onFinalize concurrently over the same non-thread-safe ArrayList and ObjectOpenHashSet (recipesLoadedByCC.clear()/add at RecipeManagerCommon.kt:124-141) while the main thread reads them from evaluateRecipesOfType during crafting.

**Arreglo.** Make the command call a real reload entry point (ResourceListener.onReload) instead of the initial-load path, clear awaitingVerificationRecipes at the start of each load cycle, guard against concurrent reloads with an AtomicBoolean, and wrap the async body in try/catch to report the failure back to ctx.source instead of returning SUCCESS_RESULT unconditionally.

> **Verificación adversarial — confirmado.** Holds; every link verified. RecipesCommand.kt:71-76 is quoted correctly and calls `resourceLoader.loadResources()` on an async task, returning SUCCESS_RESULT with no message. ResourceLoaderImpl.kt:107-126 runs onPrepare -> onInitialLoad -> onFinalize unconditionally; RecipeManagerCommon.kt:115-117 shows `onReload` is an empty TODO and is never invoked from loadResources. RecipeManagerCommon.kt:102-109 appends into `awaitingVerificationRecipes` on every onInitialLoad, and grep over the class shows only `add` (line 106) and iteration (lines 138-143) — no `clear()`, so the list accumulates. DirectorySource.kt:48-71 re-walks the directory and re-deserializes every file on each `load()` call, so the same keys are appended again. onFinalize (123-135) -> verifyRecipesAndLoad (137-144) -> `registerOrUpdateRecipes(awaitingVerificationRecipes)` (181-183) -> RecipeIndex.registerOrUpdateAll (64-83) does `byKeyBuilder.put(recipe.key, ref)` per element into a Guava `ImmutableMap.builder` and `byKeyBuilder.build()` at line 82 — Guava's build() throws IllegalArgumentException on duplicate keys regardless of value equality, so the first reload throws on the async thread. RecipeManagerCommon is registered as a listener (CustomCraftingServerSpigotLike.kt:22-24), confirming the path is live. No re-entrancy guard exists around loadResources, and recipesLoadedByCC is a plain ObjectOpenHashSet cleared/refilled at lines 125/141 while the main thread reads `index` in evaluateRecipesOfType (146-160). Minor overstatement only: reload() sends no success text to the player (it merely returns 1), so 'telling the player it succeeded' is really 'silently failing'. ALTO is appropriate.

#### 🟠 CORE-4 — Corrupt or empty sentry_id file throws out of the plugin constructor and prevents the plugin from loading

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/sentry/SentryUtils.kt:82`

```
if (idFile.exists()) {
    FileInputStream(idFile).use {
        val bytes = it.readAllBytes()
        id = UUID.fromString(String(bytes))
    }
}
```

**Impacto.** The write path above (lines 70-76) is wrapped in try/catch and swallows every exception, so a failed/partial write (disk full, read-only volume, ENOSPC) leaves a zero-byte `sentry_id` file behind. The read path is NOT guarded: UUID.fromString("") throws IllegalArgumentException, and so does any file an admin edited and saved with a trailing newline. That exception propagates out of initSentry -> setupSentry, which is invoked from the plugin/mod constructor (SpigotLoaderPlugin.kt:149 init block, CustomCraftingFabricMod.kt:74 init block), so CustomCrafting fails to instantiate and the whole plugin/mod never loads — because of a throwaway telemetry id cache file. It is also unrecoverable without the admin knowing to delete that hidden .data file.

**Arreglo.** Wrap the read in try/catch (or use runCatching) and fall back to regenerating the id: catch IllegalArgumentException/IOException, delete the bad file, write a fresh UUID. Telemetry setup must never be able to abort plugin construction.

> **Verificación adversarial — confirmado.** Holds. SentryUtils.kt:67-84 re-read: the write path (68-77) is `try { FileOutputStream(idFile).use { it.write(...) } } catch (e: Exception) { /* Ignore exceptions */ }` — a FileOutputStream that is created then fails to write leaves a zero-byte file and the exception is swallowed; the read path (79-84) `FileInputStream(idFile).use { id = UUID.fromString(String(it.readAllBytes())) }` has no try/catch, and UUID.fromString("") or any value with a trailing newline throws IllegalArgumentException/NumberFormatException. It propagates out of initSentry -> setupSentry, and both callers invoke it from a constructor init block with no guard: core/spigot/.../SpigotLoaderPlugin.kt:27-34 and core/fabric/.../CustomCraftingFabricMod.kt:29-34 (the finding's cited line numbers 149 and 74 are wrong, but the init-block mechanism it describes is exactly what is there; PaperLoaderPlugin.kt:29 is the same pattern and was not even listed). An exception from a JavaPlugin constructor / ModInitializer init aborts plugin instantiation, so the whole plugin fails over a telemetry cache file. ALTO is fair given it is an unrecoverable-without-manual-intervention startup failure, even though the trigger (partial write or hand-edited file) is uncommon.

#### 🟡 CORE-2 — Backup command zips the whole resource tree synchronously on the main server thread

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/commands/MainCommand.kt:26`

```
customCrafting.server!!.resourceManager.backupManager.createBackup()

ctx.source.sendSuccess({ Component.literal("Creating backup...") }, false)
```

**Impacto.** Brigadier command executors run on the main server thread. createBackup() -> DirectoryBackupDestination.backup() does directory.mkdirs(), source.directory.walkTopDown() over every file and `file.inputStream().use { it.copyTo(zipOutputStream) }` (DirectoryBackupDestination.kt:33-49) — full blocking disk IO plus DEFLATE compression of every recipe file. On an installation with a few thousand recipe .conf files the server hangs for seconds (watchdog territory on a slow/NFS disk) and all players freeze. The message even claims "Creating backup..." as if it were asynchronous, while the work has already completed synchronously. The reload command in the same module already shows the right pattern (ScafallProvider.get().scheduler.async in RecipesCommand.kt:72).

**Arreglo.** Wrap the createBackup() call in ScafallProvider.get().scheduler.async(customCrafting) { ... } exactly as RecipesCommand.reload does, and report completion/failure back to ctx.source afterwards (createBackup discards the Result<File> returned by each destination, so failures are invisible too).

> **Verificación adversarial — severidad corregida a MEDIO.** Holds factually. MainCommand.kt:26-28 is exactly `customCrafting.server!!.resourceManager.backupManager.createBackup()` followed by `ctx.source.sendSuccess({ Component.literal("Creating backup...") }, false)` — the call is a plain synchronous call on the Brigadier executor (main server thread), and the "Creating backup..." message is indeed sent after the work already finished. BackupManagerImpl.kt:11-15 loops destinations synchronously; DirectoryBackupDestination.kt:43-65 does `directory.mkdirs()`, `source.directory.walkTopDown().filter{isFile}` and `file.inputStream().use { it.copyTo(zipOutputStream) }` into a ZipOutputStream — blocking IO plus DEFLATE, no scheduler, no Result handling (the returned Result is discarded by BackupManagerImpl). The async pattern does exist in the same module (RecipesCommand.kt:72 `ScafallProvider.get().scheduler.async`). Severity lowered to MEDIO: as a standalone defect this is main-thread blocking IO over a config tree (typically sub-second for a few thousand small .conf files); the 'any player can spam it' amplification is already counted in CORE-1, and watchdog-level stalls require an extreme file count or a slow network volume.

#### 🟡 CORE-5 — Sentry Log4J appender is attached to the global root logger and never removed; Sentry is never closed on disable

**Severidad:** MEDIO · **Categoría:** `resource-leak` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/sentry/SentryUtils.kt:54`

```
sentryAppender.start()
        val logCtx = LoggerContext.getContext(false)
        val logConfig = logCtx.configuration
        logConfig.addAppender(sentryAppender)
        logConfig.rootLogger.addAppender(sentryAppender, Level.ERROR, null)
        logCtx.updateLoggers()
```

**Impacto.** The LoggerContext resolved here is the server's (Log4j's ClassLoaderContextSelector walks up the plugin classloader's parents to the server context), and it outlives the plugin. Nothing anywhere in the repo calls Sentry.close(), appender.stop() or removeAppender — grep for 'Sentry.' finds only configureScope/init, and CustomCraftingServerSpigotLike.onUnload() (line 40-42) is empty. After a Bukkit `/reload confirm` the plugin is re-instantiated with a fresh classloader and setupSentry runs again: a second SentryAppender instance is added to the same root LoggerConfig (the name-keyed Configuration map is bypassed by rootLogger.addAppender, which keys on the AppenderControl/instance), so every subsequent ERROR is captured twice, three times, N times, and the old appender keeps the previous plugin classloader alive — a classloader leak on every reload. The global uncaught-exception handler installed by isEnableUncaughtExceptionHandler (line 94) is likewise never uninstalled.

**Arreglo.** Keep a reference to the created appender and add a teardown (called from the module's unload path) that does rootLogger.removeAppender(name), logCtx.updateLoggers(), appender.stop() and Sentry.close(); make setupSentry idempotent by bailing out if an appender with that name is already present on the root logger.

#### 🟡 CORE-6 — Error telemetry is hard-enabled in the jar with no server-side opt-out and is started before any config is read

**Severidad:** MEDIO · **Categoría:** `security` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/sentry/SentryUtils.kt:87`

```
Sentry.init {
    it.isEnabled = CustomCraftingProperties.sentryEnabled
    it.dsn = CustomCraftingProperties.sentryDsn

-- and core/common/src/main/resources/com/wolfyscript/customcrafting/vals.properties:3
sentry.enabled=true
```

**Impacto.** sentryEnabled/sentryDsn come only from vals.properties, a resource baked into the jar at build time (core/common/build.gradle.kts processResources). There is no key for sentry anywhere in ConfigurationManagerImpl/ResourceSettings (grep -i sentry over all .kt/.conf/.yml finds nothing else), so a server owner cannot turn off outbound error reporting without repacking the jar. Worse, setupSentry runs inside the plugin/mod constructor (SpigotLoaderPlugin.kt:149, CustomCraftingFabricMod.kt:74) — before configurationManager.load() — so a config toggle could not take effect even if one were added later. Every ERROR logged by a com.wolfyscript logger, plus the full installed-plugin list and versions added by CustomCraftingSpigot/Paper.onInit (scope.setContexts("bukkit.plugins", ...)), is transmitted to errors.wolfyscript.com from the first tick. The stable per-install UUID makes those reports linkable across restarts.

**Arreglo.** Read an opt-out (or opt-in) flag from the plugin's own config/data dir before Sentry.init — e.g. a plain `telemetry` file or a property in resources.conf checked inside setupSentry — and default isEnabled to that; document it. Keep the compile-time property only as the upper bound.

#### 🟡 CORE-7 — exportResource creates the destination file before checking the resource exists and fails silently, permanently poisoning the default config

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/util/ResourceUtils.kt:13`

```
fun exportResource(pathToResource: String, outDest: File) {
    if (!outDest.exists()) {
        if (!outDest.parentFile.mkdirs() && !outDest.createNewFile()) {
            return
        }
    }

    val inputStream = getResourceAsStream(pathToResource)
    if (inputStream == null) {
        return
    }
    val outputStream = FileOutputStream(outDest)
```

**Impacto.** outDest is created (createNewFile) before the classpath resource is looked up, and the null-resource branch returns without any log line. The single caller is ConfigurationManagerImpl.saveDefaults (line 88-94), which only exports when `!resourcesSettingsFile.exists()`. So if the resource is missing or renamed (relocation/shading mistake), or if the copy dies half-way (IOException, disk full — nothing catches it), a zero-byte/truncated resources.conf is left on disk; on this and every later start saveDefaults skips the export because the file now exists, and the very next line, configMapper.readValue<ResourceSettings>(resourcesSettingsFile), throws Jackson MismatchedInputException ("No content to map due to end-of-input") from the ConfigurationManagerImpl init block — the plugin never starts again until the admin deletes the empty file. Secondary: if the FileOutputStream constructor at line 24 throws, the InputStream obtained at line 20 is leaked (it is only closed inside the `use` below).

**Arreglo.** Look up the resource first and return with an error log if it is null; write to a temp file and move it into place atomically on success; wrap both streams in a single use/try so the input stream is closed if opening the output fails; delete a partially written destination on IOException.

#### 🟡 CORE-9 — Recipe tab-completion allocates the full key list on every keystroke and matches case-sensitively

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/commands/RecipesCommand.kt:41`

```
CustomCraftingProvider.get().server!!.recipeManager.recipesLoadedByCC
    .map { it.toString() }
    .filter { it.startsWith(builder.remaining) }
    .forEach { builder.suggest(it) }
```

**Impacto.** The vanilla client sends a completion request on every character typed. Each request runs on the main server thread and allocates one String per registered recipe plus two intermediate ArrayLists before filtering: with 5.000 CC recipes that is 10.000 objects per keystroke, i.e. ~100k short-lived objects while an operator types a recipe id. The same pattern is repeated for `enable` over disabledRecipes (line 58). Additionally the prefix test uses builder.remaining, not builder.remainingLowerCase, so typing `CC:` or any uppercase character yields zero suggestions even though `Key.toString()` is lowercase — vanilla's SharedSuggestionProvider.suggestResource lowercases precisely to avoid this.

**Arreglo.** Use SharedSuggestionProvider.suggestResource(keys.asSequence().map{...}, builder) or at minimum iterate the set directly (no .map/.filter intermediates) and compare against builder.remainingLowerCase.

#### ⚪ CORE-10 — /recipes disable reports success for a recipe key that does not exist

**Severidad:** BAJO · **Categoría:** `correctness` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/commands/RecipesCommand.kt:36`

```
CustomCraftingProvider.get().server!!.recipeManager.disableRecipe(recipeKey)

ctx.source.sendSuccess({ Component.literal("Disabled Recipe $recipeKey") }, false)
```

**Impacto.** RecipeManagerCommon.disableRecipe (line 162-167) only records the key when `index.get(recipe) != null`, otherwise it silently does nothing. Since IdentifierArgument accepts any well-formed namespaced id, an operator who mistypes (`/recipes disable customcrafting:sword_recip`) is told "Disabled Recipe customcrafting:sword_recip" while the real recipe stays enabled — the operator believes an exploitable recipe is off when it is not. enableRecipe has the mirror problem (it never validates the key at all).

**Arreglo.** Have disableRecipe/enableRecipe return a Boolean (or check recipeManager.getRecipe(key) != null in the command) and send a failure component via ctx.source.sendFailure when the key is unknown.

#### ⚪ CORE-11 — Properties resource stream is never closed and a missing resource throws from the static initializer

**Severidad:** BAJO · **Categoría:** `resource-leak` · **Ubicación:** `core/common/src/main/kotlin/com/wolfyscript/customcrafting/core/util/CustomCraftingProperties.kt:16`

```
init {
    properties.load(javaClass.classLoader.getResourceAsStream("com/wolfyscript/customcrafting/vals.properties"))
}
```

**Impacto.** getResourceAsStream returns a JarURLConnection-backed stream that is never closed, keeping a jar entry handle open for the JVM lifetime. More importantly the result is passed to Properties.load without a null check: if the resource is absent from the shaded jar (note core/common/build.gradle.kts:16 restricts processResources to `include("**/*.properties")`, so any packaging change here is easy to get wrong) the object initializer throws NPE, which surfaces as ExceptionInInitializerError on the first touch of CustomCraftingProperties.sentryEnabled — i.e. inside Sentry.init, inside the plugin constructor, aborting plugin load with a confusing stack trace.

**Arreglo.** `javaClass.classLoader.getResourceAsStream(...)?.use { properties.load(it) }` and log a warning (or fall back to defaults) when it is null.

#### ⚪ CORE-12 — DataManager duplicates its DATA_PATH constant and takes an unused CustomCrafting parameter

**Severidad:** BAJO · **Categoría:** `dead-code` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/data/DataManager.kt:12`

```
const val DATA_PATH = ".data"

fun createNewForDir(customCrafting: CustomCrafting, directory: File): DataManager {
    return DataManagerImpl(directory)
}

-- DataManagerImpl.kt:8 declares the identical constant again:
companion object { const val DATA_PATH = ".data" }
override val storageDir: File = File(rootDir, DATA_PATH)
```

**Impacto.** Two independent copies of the same path constant: the impl resolves storageDir with ITS copy, while callers build paths with the interface's copy — SpigotLoaderPlugin.kt:152 and CustomCraftingFabricMod.kt:77 both compose the Sentry dir as dataFolder/customcrafting/.data using DataManager.DATA_PATH. They agree today, so nothing breaks, but changing one of them silently splits the storage location from the sentry-id location. The customCrafting parameter of createNewForDir is ignored entirely (all three call sites pass `this`), which misleads readers into thinking the manager is module-scoped.

**Arreglo.** Delete the companion constant in DataManagerImpl and use DataManager.DATA_PATH, and drop the unused customCrafting parameter (or actually use it).

#### ⚪ CORE-8 — Condition and Transmuter registries are created but never bound to Jackson like their four siblings

**Severidad:** BAJO · **Categoría:** `config` · **Ubicación:** `core/api/src/main/kotlin/com/wolfyscript/customcrafting/core/registry/CustomCraftingRegistriesCommon.kt:77`

```
fun registerJacksonTypes() {
    registerTypeRegistry(ResultAction::class.java, get(CustomCraftingRegistryTypes.resultActions.key).getOrThrow())
    registerTypeRegistry(IngredientConsumer::class.java, ...)
    registerTypeRegistry(IngredientMatcher::class.java, ...)
    registerTypeRegistry(IngredientRemainder::class.java, ...)
}

-- but lines 46-47 create two more registries that are never passed to registerTypeRegistry:
createRegistry(CustomCraftingRegistryTypes.recipeConditionTypes) { RegistrySimple(it) }
createRegistry(CustomCraftingRegistryTypes.recipeItemTransmuters) { RegistrySimple(it) }
```

**Impacto.** Condition (condition/Condition.kt:15-16) and Transformation.Transmuter (modifier/Transformation.kt:45-46) carry exactly the same @JsonTypeInfo(Id.CUSTOM) + @JsonTypeIdResolver(RegistryKeyTypeIdResolver::class) pair as ResultAction, which only resolves type ids because its registry was handed to registerTypeRegistry here. Their KDoc advertises third-party registration as a supported extension point ("Custom Condition types can be registered too from third-parties"), but a third party that registers a Condition class into recipeConditionTypes will still fail to deserialize a recipe whose conditions list contains that type, because the resolver has no registry bound for the Condition base type. Note I could not read scafall's RegistryKeyTypeIdResolver (external dependency, sources not in the repo), so this rests on the 4-of-6 asymmetry with ResultAction rather than on reading the resolver body.

**Arreglo.** Add registerTypeRegistry(Condition::class.java, get(CustomCraftingRegistryTypes.recipeConditionTypes.key).getOrThrow()) and the same for Transformation.Transmuter / recipeItemTransmuters.


### Listeners de Bukkit/Paper — `core/spigotlike` + `core/spigot` + `core/paper`

<details><summary>Cobertura declarada por el lector (28 ficheros)</summary>

Read in full all 12 files of core/spigotlike/src (AnvilListener, CampfireListener, CauldronListener, CrafterListener, CraftingListener, FurnaceListener, GrindstoneListener, SmithingListener, ListenerUtil, RecipeDisplays, RecipePlaceholders, RecipeUtils, RecipeSeeds, CustomCraftingServerSpigotLike), all 4 files of core/spigot/src and all 4 files of core/paper/src (incl. StonecutterListener). For every listener I traced which events are registered (ListenerUtil.kt:8-15 + CustomCraftingServerPaper.kt:15 - each listener is registered exactly once, no duplicate registration), the priority, and every inventory mutation path. To validate the dupe/consumption claims I also opened 8 files outside the slice that the listeners call into: CustomRecipeCraftingImpl.shrink, CraftingFormulaShapedImpl, CraftingFormulaShapelessImpl, CraftingMatrixData(+Impl), IngredientImpl, IngredientConsumerConsumeImpl/KeepImpl/ReplaceImpl. NOT covered / not audited: the scafall wrappers themselves (wrap()/unwrapSpigot() mirror-vs-copy semantics are external sources I could not read, so I avoided any finding that depends on them), the editor/ui/fabric modules, and RecipePlaceholders/RecipeDisplays NMS registration paths beyond reading them (registerDisplayRecipes at RecipeDisplays.kt:37-42 looks dead - RecipeReference.toDisplay() always returns null because the when-branch result is discarded - and CustomRecipeCrafting.toDisplay() builds its key with toPlaceholderRecipeKey() instead of toDisplayRecipeKey(), so isDisplay() could never be true; I left it out of the findings because "display recipes" may simply not be wired up yet in this alpha). One defect I confirmed but is NOT rooted in my files: CustomRecipeCraftingImpl.shrink (core/api, line 40) indexes `input.matrixData.matrix[value.recipeIndex]`, which for shapeless recipes is the recipe-ingredient index, not the trimmed-matrix index - it shrinks the wrong stack and writes it to another slot. I report only the spigotlike half of that ordering bug (BUKKIT-10).

</details>

#### 🔴 BUKKIT-1 — Anvil shift-click duplicates the result: item is added to the inventory AND put on the cursor

**Severidad:** CRITICO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/AnvilListener.kt:99`

```
if (event.isShiftClick) {
    if (event.view.bottomInventory.addItem(resultStack).isNotEmpty()) {
        return
    }
}
if (cursor.type == Material.AIR) {
    Bukkit.getScheduler().runTask(plugin, Runnable {
        event.view.setCursor(resultStack)
    })
```

**Impacto.** A shift-click is only possible with an empty cursor, so `cursor.type == Material.AIR` is true on exactly the path that just succeeded in `addItem(resultStack)`. The `if (event.isShiftClick)` block does not return when the add succeeds, so execution falls into the `cursor.type == AIR` branch and schedules `setCursor(resultStack)` for the next tick. Player puts a custom repairing recipe in an anvil and shift-clicks slot 2: he gets the result in his inventory AND a second copy on his cursor, while only one set of ingredients (lines 161-178) and one level cost (line 122) is charged. Repeatable at will -> unlimited item duplication.

**Arreglo.** Return from the handler after a successful shift-click transfer (or wrap the cursor branches in `else`): `if (event.isShiftClick) { if (addItem(...).isNotEmpty()) return; } else if (cursor.type == Material.AIR) { ... } else if (cursor.isSimilar(resultStack)) { ... }`.

> **Verificación adversarial — confirmado.** AnvilListener.kt:99-107 matches the quote exactly. The shift-click block only returns when addItem leaves leftovers (isNotEmpty); on full success it falls through, and since a shift-click always has an empty cursor, line 104 (cursor.type == AIR) is true and line 106 schedules setCursor(resultStack) next tick. resultStack is event.currentItem (line 80), and CraftInventory.addItem does not zero the passed stack in the simple free-slot path, so the same amount lands on the cursor. Levels (line 122) and ingredients (lines 161-178) are charged once. No guard upstream: event.result = DENY (line 88) only stops vanilla, not the scheduled setCursor. Duplication confirmed.

#### 🔴 BUKKIT-2 — Smithing shift-click duplicates the result exactly like the anvil path

**Severidad:** CRITICO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/SmithingListener.kt:136`

```
if (event.isShiftClick) {
    if (event.view.bottomInventory.addItem(resultStack).isNotEmpty()) {
        return
    }
}
// A quick implementation to collect the result. Things like moving the item to the hotbar won't work!
if (event.cursor.type == Material.AIR) {
    Bukkit.getScheduler().runTask(plugin, Runnable {
        event.view.setCursor(resultStack)
    })
```

**Impacto.** Same control-flow defect as BUKKIT-1. On a shift-click the cursor is empty, so after `addItem` succeeds the code also schedules `setCursor(resultStack)`. Player shift-clicks slot 3 of a smithing table holding a custom smithing recipe: one result lands in the inventory, a second identical result appears on the cursor, while `shrinkIngredient` (lines 160-164) consumes the inputs only once -> duplication of any custom smithing result.

**Arreglo.** Make the shift-click branch terminal (`return` after a successful transfer) and turn the cursor handling into the `else` branch.

> **Verificación adversarial — confirmado.** SmithingListener.kt:136-153 is identical in structure; resultStack comes from inventory.result (line 129), the shift-click block at 136-140 returns only on leftovers, and line 142 (cursor AIR, always true for a shift-click) schedules setCursor(resultStack). Ingredients are consumed once via shrinkIngredient at lines 160-164 and inventory.contents (3 elements) also clears the result slot, so the inventory copy plus the cursor copy are two results for one set of inputs. Note the result slot is filled by the registered placeholder SmithingTransformRecipe (RecipePlaceholders.kt:145-158), so the path is reachable in practice.

#### 🔴 BUKKIT-3 — Stonecutter result handler fires on player-inventory slot 1 because it checks the top inventory instead of the clicked one

**Severidad:** CRITICO · **Categoría:** `bug` · **Ubicación:** `core/paper/src/main/kotlin/com/wolfyscript/customcrafting/paper/recipes/StonecutterListener.kt:62`

```
val inventory = event.inventory as? StonecutterInventory ?: return
val player = event.whoClicked as? Player ?: return
if (event.slot != RESULT_SLOT) {
    return
}
```

**Impacto.** `InventoryClickEvent.getInventory()` returns the *top* inventory of the view, not the inventory that was clicked, and `getSlot()` is the index inside the *clicked* inventory. With a stonecutter open, a shift-click on slot index 1 of the player's own inventory satisfies both checks. Flow then reaches `collectResultAndRunActions` (line 80) with `isShiftClick == true`, which runs `quickCraft` and inserts `maxPossible` copies of the recipe result into the player inventory. Because nothing is ever written back to the stonecutter input (see BUKKIT-4/BUKKIT-12 evidence at line 90) the player can repeat the shift-click indefinitely: unlimited free items from a single stonecutter input.

**Arreglo.** Use the clicked inventory: `val inventory = event.clickedInventory as? StonecutterInventory ?: return` (and keep `event.slot != RESULT_SLOT`), mirroring what GrindstoneListener.kt:39 and CraftingListener.kt:54 do.

> **Verificación adversarial — confirmado.** StonecutterListener.kt:62-66 uses event.inventory (InventoryEvent.getInventory() = the view's TOP inventory) while event.slot is the index inside the CLICKED inventory (InventoryView.convertSlot). A shift-click on player-inventory index 1 (second hotbar slot) with a stonecutter open therefore passes both checks, reaches collectResultAndRunActions (line 80) with isShiftClick true, which runs quickCraft (RecipeUtils.kt:47-50) and inserts maxPossible results. maxPossible is computed from listOf(result.wrap()) (line 85), i.e. from the RESULT amount, not the input, so a 4x result yields 4 crafts per click while shrink (lines 90-100) at best removes 4 from the input. The handler firing on a wrong-inventory click is confirmed by the code; item multiplication follows.

#### 🔴 BUKKIT-4 — Grindstone: inverted isSimilar check merges a foreign result onto the cursor and consumes ingredients even when nothing was collected

**Severidad:** CRITICO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/GrindstoneListener.kt:72`

```
} else if (!cursor.isSimilar(result)) {
    val increasedAmount = cursor.amount + result.amount
    if (increasedAmount > cursor.maxStackSize) {
        return
    }
    cursor.amount = increasedAmount
    event.currentItem = null
}
```

**Impacto.** The condition is negated. (a) Dupe: player holds 1 diamond on the cursor and clicks the grindstone result slot of a custom grinding recipe whose result is, say, a stick. `!isSimilar` is true, so `cursor.amount = 1 + 1 = 2` -> he now has 2 diamonds, and the ingredients are consumed at lines 97-107. Repeat with a 63-stack cursor to farm items. (b) Item loss: when the cursor genuinely holds the same item as the result, none of the branches match, nothing is collected, yet execution still falls through to lines 84-107, spawning the XP orb and decrementing both input slots - the recipe is paid for and the result is destroyed.

**Arreglo.** Use `else if (cursor.isSimilar(result))` and, in the amount branch, add `result.amount` of the *result* to the cursor only after verifying similarity; additionally guard the consumption block (lines 81-110) behind a boolean that is only set when the result was actually collected.

> **Verificación adversarial — confirmado.** GrindstoneListener.kt:72 literally reads 'else if (!cursor.isSimilar(result))' and then merges result.amount into the cursor stack (lines 73-77). event.cursor is a CraftItemStack mirror of the carried stack, and the event is cancelled at line 54 so vanilla never restores it, meaning a non-matching cursor stack grows by the result amount while the inputs are decremented at lines 97-107. The mirror case (cursor genuinely similar to the result) matches no branch, so nothing is collected yet the XP orb (86-90), actions (92-94) and both input decrements still run. Both halves of the finding are in the real code.

#### 🟠 BUKKIT-10 — possibleResultAmount pairs ingredients with source stacks by list position, which is wrong for shapeless recipes

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/RecipeUtils.kt:13`

```
recipeEvaluationResult.data.nonNullIngredients.withIndex().minOf { (index, value) ->
    sourceStacks[index].amount / value.matchedItemStackRef.amount
}
```

**Impacto.** `sourceStacks` is `matrixData.flatItems`, i.e. the non-empty stacks in *grid* order (CraftingMatrixDataImpl.kt:75), while `nonNullIngredients` is `ingredients.filterNotNull()` in *recipe-ingredient* order. CraftingFormulaShapelessImpl.kt:58 stores each match at `pickedIngredients[ingrdRecipeIndex]`, so for shapeless recipes the two orders diverge whenever the player's placement order differs from the recipe's ingredient order. Concrete: shapeless recipe = [1 diamond, 4 coal]; player puts 4 coal in grid slot 0 and 1 diamond in slot 1. The pairing becomes coal(4)/diamond-requirement(1)=4 and diamond(1)/coal-requirement(4)=0, so `minOf` returns 0. `collectResultAndRunActions` returns 0 at RecipeUtils.kt:29-31, the click is still cancelled at CraftingListener.kt:75, and the player gets nothing - the recipe is simply uncraftable depending on where he drops the items. Each entry also allocates an IndexedValue per ingredient on every result-slot click.

**Arreglo.** Index the source stacks by slot instead of by position: use `value.invSlot` against the full matrix (or `matrixData.flatItemIndices.indexOf(value.invSlot)`), and replace `withIndex().minOf` with a plain loop to avoid the per-click IndexedValue allocations.

> **Verificación adversarial — confirmado.** RecipeUtils.kt:12-15 pairs sourceStacks[index] with nonNullIngredients[index]. nonNullIngredients is ingredients.filterNotNull() over the array indexed by recipe position (EvaluationResultImpl.kt:30; CraftingFormulaShapelessImpl.kt:58 stores at pickedIngredients[ingrdRecipeIndex]), while sourceStacks is matrixData.flatItems, i.e. grid order (CraftingMatrixDataImpl.kt:75). The shapeless matcher (lines 47-68) walks flat items and picks the first matching ingredient, so the two orders diverge whenever placement order differs from ingredient order. The worked example (4 coal in slot 0, 1 diamond in slot 1 for [diamond x1, coal x4]) really yields min(4/1, 1/4) = 0, and collectResultAndRunActions returns 0 at RecipeUtils.kt:29-31 while CraftingListener.kt:75 has already cancelled the click. Holds.

#### 🟠 BUKKIT-11 — Crafter block caches the previous recipe in PDC but only clears it on a player click, so hopper-fed crafters jam permanently

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/CrafterListener.kt:62`

```
val data = if (previousRecipe != null) {
    previousRecipe.value?.evaluate(input, context)?.let { RecipeEvaluationResult.of(previousRecipe, it) }
} else {
    customCrafting.server!!.recipeManager.evaluateRecipesOfType(...)
}
```

**Impacto.** When the PDC holds a previous recipe key, only that one recipe is evaluated; if it no longer matches, `data` is null and the code never falls back to a full lookup. The PDC is only cleared in `onCrafterInvClick` (line 127), which requires a *player* to click the crafter inventory. A crafter fed by hoppers/droppers never sees an InventoryClickEvent. Sequence: hopper fills the crafter for custom recipe A (PDC=A), the item filter later delivers the ingredients of custom recipe B; A no longer matches, `data` is null, execution reaches line 100 where the registered placeholder recipe is detected and `event.isCancelled = true` - the crafter produces nothing, forever, and there is no automated way to recover.

**Arreglo.** On a `previousRecipe` miss, fall through to the full `evaluateRecipesOfType` lookup (and remove/overwrite the PDC key) instead of treating null as 'no custom recipe'.

> **Verificación adversarial — confirmado.** CrafterListener.kt:62-70 matches the quote: when the PDC key resolves to a recipe, only that recipe is evaluated and there is no fallback to evaluateRecipesOfType when the evaluation returns null. The PDC is written at 75-79 and persisted by state.update(true) at 91, and the only removal is in onCrafterInvClick (line 127), which requires an InventoryClickEvent on the CrafterInventory - hopper/dropper feeding never fires that. With data null, execution reaches line 100 where the placeholder bukkit recipe causes event.isCancelled = true, so the crafter stalls with no automated recovery. Confirmed.

#### 🟠 BUKKIT-12 — Stonecutter: normal left-click with an empty cursor is always cancelled, and the consumed input is discarded

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/paper/src/main/kotlin/com/wolfyscript/customcrafting/paper/recipes/StonecutterListener.kt:72`

```
if (!event.isShiftClick && (!result.isSimilar(event.cursor) || result.amount + event.cursor.amount > event.cursor.maxStackSize)) {
    event.isCancelled = true
    return
}
```

**Impacto.** With an empty cursor `event.cursor` is an AIR stack, so `result.isSimilar(AIR)` is false and the negated term is true: every ordinary click on the stonecutter result slot is cancelled and returns before the collection code. A custom stonecutting result can only ever be taken by shift-clicking or by already holding a matching stack - the normal take path is dead. Compounding it, lines 90-100 call `source.selectedIngredient.shrink(...)` and throw the return value away; `IngredientConsumerKeepImpl.consume` returns the target untouched and `IngredientConsumerReplaceImpl.consume` returns a brand-new stack, so for those consumers the input slot is never updated and the source item is never consumed. And `collectResultAndRunActions` is handed `listOf(result.wrap())` (line 85) - the *result* stack, not the input - so `possibleResultAmount` computes the craft count from the result amount (input 1 stone, result 4 slabs => maxPossible 4).

**Arreglo.** Invert the cursor test (`event.cursor.type != AIR && (!result.isSimilar(cursor) || overflow)`), write the shrink return value back with `inventory.setItem(INPUT_SLOT, ...)`, and pass the input stack (not the result) as the source stack list.

> **Verificación adversarial — confirmado.** StonecutterListener.kt:72 is verbatim; with an empty cursor event.cursor is an AIR stack, result.isSimilar(AIR) is false, so !isSimilar is true and every non-shift click is cancelled at 73-74 before any collection - the plain take path is unreachable. Lines 90-100 do discard the return of selectedIngredient.shrink, and IngredientConsumerKeepImpl.kt:19-21 returns the target unchanged while IngredientConsumerReplaceImpl.kt:42-44 returns a brand new stack (IngredientImpl.kt:37-45 just delegates), so for those consumers the input slot is never updated. Line 85 does pass listOf(result.wrap()) into possibleResultAmount, so maxPossible is derived from the result amount. All three sub-claims verified.

#### 🟠 BUKKIT-13 — Furnace: backing-recipe usage counter is incremented in a detached container and never stored, so overridden vanilla XP is never subtracted

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/FurnaceListener.kt:207`

```
val recipeCount: Int =
    usedBackingRecipes.getOrDefault(backingRecipe, PersistentDataType.INTEGER, 0)
usedBackingRecipes.set(backingRecipe, PersistentDataType.INTEGER, recipeCount + 1)
```

**Impacto.** `usedBackingRecipes` is either a fresh detached container (line 203) or a *copy* returned by `rootContainer.get(...)`; PersistentDataContainer sub-containers are values, not views. Lines 223-227 only write `customRecipesUsedKey` back - `backingRecipesUsedKey` is never stored. Consequently `onCollectExperience` reads an empty map at line 241 and the loop at 245-249 subtracts nothing. A custom cooking recipe that overrides a vanilla smelting recipe therefore pays out vanilla XP *plus* custom XP: smelt 64 items with a custom recipe worth 5 xp overriding iron ore (0.7 xp) and the player collects 64*0.7 xp he was never supposed to get. The whole 'subtract the backing recipe' mechanism documented at lines 169-182 is inert.

**Arreglo.** Add `rootContainer.set(backingRecipesUsedKey, PersistentDataType.TAG_CONTAINER, usedBackingRecipes)` before `blockState.update()` at line 228.

> **Verificación adversarial — confirmado.** FurnaceListener.kt:198-207 gets backingRecipesUsedKey as a TAG_CONTAINER (a value copy, or a fresh detached container from adapterContext at 203) and increments it at 207, but the only write-back is rootContainer.set(customRecipesUsedKey, ...) at 223-227 followed by blockState.update() at 228 - backingRecipesUsedKey is never stored there. onCollectExperience reads it at line 241 and therefore always gets an empty map, so the subtraction loop at 245-249 is a no-op; the only other writes to that key are the clearing set at 275-279. The documented backing-recipe subtraction (comments at 169-182) is inert. Confirmed.

#### 🟠 BUKKIT-14 — Anvil/Smithing: when the cursor holds a different item the ingredients and levels are still consumed and the result is destroyed

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/AnvilListener.kt:104`

```
if (cursor.type == Material.AIR) {
    ...
} else if (cursor.isSimilar(resultStack)) {
    ...
}

// At this point, the result was successfully picked up and all requirements are satisfied.
// Continue to process level, actions, ingredients, etc.

player.level -= view.repairCost
```

**Impacto.** Neither branch matches when the cursor holds an item that is not similar to the result, yet the comment's assumption ('the result was successfully picked up') is not enforced - execution continues unconditionally. Player holds a block of dirt on the cursor and left-clicks the anvil result slot (vanilla does nothing in this situation): he loses `view.repairCost` levels, the base and addition stacks are decremented at lines 161-178, `event.currentItem = null` (line 159) deletes the result, and he receives nothing. SmithingListener.kt:142-153 has the identical fall-through. Related: in creative mode `player.level += view.repairCost` at line 90 is not undone on the early returns at lines 101 and 111, so a creative player with a full inventory farms free XP levels by shift-clicking.

**Arreglo.** Track whether the result was actually transferred and `return` before the level/ingredient consumption when it was not; move the creative level top-up so it is symmetric with every return path.

> **Verificación adversarial — confirmado.** AnvilListener.kt:104-117 has only the AIR and isSimilar branches, with no else and no return, and execution continues unconditionally to player.level -= view.repairCost at 122, event.currentItem = null at 159 and the ingredient decrements at 161-178. A cursor holding a dissimilar item therefore pays the cost and destroys the result. SmithingListener.kt:142-153 has the same fall-through into runActions/shrinkIngredient at 157-164. The creative-mode detail also checks out: line 90 adds repairCost levels before the early returns at 101 and 111, which never undo it.

#### 🟠 BUKKIT-5 — Grindstone ingredient placement wipes the whole cursor stack (min instead of max)

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/GrindstoneListener.kt:167`

```
val possible = min(cursor.amount, currentItem.maxStackSize - curAmount)
event.currentItem!!.amount += possible
event.cursor.amount = min(0, cursor.amount - possible)
event.isCancelled = true
```

**Impacto.** `min(0, x)` is always 0 for any non-negative x, so the cursor is emptied regardless of how much was actually moved. Concrete: the grindstone slot 0 already holds an item stack that is similar to the cursor and is full (or has maxStackSize 1, e.g. two identical damaged iron swords - one in slot 0, one on the cursor). `possible` computes to 0, `currentItem.amount += 0` changes nothing, then `cursor.amount = min(0, 1) = 0` deletes the player's sword. The event is cancelled at line 168 so vanilla never restores it. Silent item destruction on an ordinary left-click.

**Arreglo.** `event.cursor.amount = max(0, cursor.amount - possible)` (the same file already uses `max` correctly at lines 187 and 196).

> **Verificación adversarial — confirmado.** GrindstoneListener.kt:165-168 is verbatim as quoted, with kotlin.math.min imported at line 30. min(0, cursor.amount - possible) is 0 for every non-negative argument, so event.cursor.amount is forced to 0 after a partial (or zero) transfer, and event.isCancelled = true (line 168) prevents vanilla resync from restoring it. Any left-click placing a cursor stack onto a similar stack in grindstone slot 0/1 where possible < cursor.amount destroys the remainder. Confirmed.

#### 🟠 BUKKIT-7 — craftingDataCache is never invalidated when no custom recipe matches, so a stale recipe is applied to the next craft

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/CraftingListener.kt:117`

```
} else {
    val recipe = e.recipe
    // No valid custom recipes found
    if (recipe !is Keyed) return
... (lines 130-143, none of which call craftingDataCache.invalidate)
    //At this point the vanilla recipe is valid and can be crafted
    Bukkit.getScheduler().runTask(plugin, Runnable { player.updateInventory() })
```

**Impacto.** `matrixDataCache` is refreshed every PrepareItemCraftEvent (line 104) but `craftingDataCache` is only written on a match (line 109); none of the no-match branches clear it, unlike SmithingListener.kt:62 and GrindstoneListener.kt:115 which invalidate at the top of their prepare handlers. Sequence: player lays out a custom recipe (cache = custom recipe R), then changes the grid so only a *vanilla* recipe matches (line 143 path, vanilla result shown). He clicks the result slot: `onCraft` finds the stale R, cancels the event and hands out R's computed result instead of the vanilla one, and shrinks the grid according to R's ingredients - slots that are not ingredients of R are set to null by the `Array(size){null}` matrix at line 88. Wrong item handed out plus destruction of the grid contents that R does not know about.

**Arreglo.** Call `craftingDataCache.invalidate(player.uniqueId)` at the start of `onPreCraft` (right after computing the matrix), exactly as SmithingListener and GrindstoneListener do.

> **Verificación adversarial — confirmado.** CraftingListener.kt:104 puts matrixData on every PrepareItemCraftEvent while craftingDataCache is only written inside the match branch at line 109; the no-match branches at 116-144 return without calling craftingDataCache.invalidate (only the catch at 151 and onCloseInv/onQuit at 159/165 do). onCraft (68-79) then gets a fresh matrix plus a stale recipe, cancels the click at line 75, hands out the stale recipe's computed result and rebuilds the grid from Array(inventory.matrix.size){null} at line 88 where only shrink's callback slots are refilled, so non-ingredient slots are nulled. Contrast confirmed: SmithingListener.kt:62 and GrindstoneListener.kt:115 invalidate at the top of prepare. Holds.

#### 🟠 BUKKIT-8 — AnvilListener.recipeCache is unbounded and has no close/quit eviction

**Severidad:** ALTO · **Categoría:** `memory-leak` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/AnvilListener.kt:36`

```
private val recipeCache = Caffeine.newBuilder().build<UUID, RecipeEvaluationResult<RecipeEvaluationResult.RepairingRecipeData, CustomRecipeRepairing>>()
```

**Impacto.** No `maximumSize`, no `expireAfter`, no `weakKeys`, and - unlike SmithingListener (onClose:197/onQuit:203) and GrindstoneListener (onClose:238/onQuit:245) - AnvilListener registers no InventoryCloseEvent or PlayerQuitEvent handler at all. Every player who ever opens an anvil with a matching custom repairing recipe leaves one RecipeEvaluationResult (which strongly references the recipe, the matched ItemStackRefs and the EvaluationContext) pinned by his UUID forever; on a server with high player churn the map grows without bound. The same missing invalidation also makes the entry stale: `onPrepare` returns at lines 52/53 without clearing, so after switching to an input combination that only vanilla can handle, `onTake` still finds the old custom data and consumes ingredients according to it (e.g. a stale recipe with no slot-1 ingredient leaves the addition item uncharged).

**Arreglo.** Add `@EventHandler onClose(InventoryCloseEvent)`/`onQuit(PlayerQuitEvent)` invalidation, invalidate at the top of `onPrepare` before re-evaluating, and give the cache a bounded `expireAfterAccess`.

> **Verificación adversarial — confirmado.** AnvilListener.kt:36 is exactly 'Caffeine.newBuilder().build<UUID, ...>()' with no maximumSize/expiry/weakKeys (CraftingListener.kt:44 does use weakKeys), and the whole file contains only onPrepare, onTake and getRepairingSeed - no InventoryCloseEvent or PlayerQuitEvent handler, unlike SmithingListener.kt:196-206 and GrindstoneListener.kt:238-247. recipeCache.invalidate appears only at line 182 on a completed craft. The staleness consequence is also real: onPrepare returns at 52/53 without clearing, so onTake (85) still finds the old RecipeEvaluationResult and consumes ingredients per data.data.bySlot (165-175) for an input combination the custom recipe no longer matches.

#### 🟠 BUKKIT-9 — Recipe result actions run twice per craft (collectResultAndRunActions and shrink both call runActions)

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/CraftingListener.kt:89`

```
val count: Int = collectResult(event, player, craftingData, matrixData, context)
val input = RecipeInput.CraftingRecipeInput.of(matrixData)

val matrix: Array<ItemStack?> = Array(inventory.matrix.size) { null }
recipe.shrink(input, craftingData, context, count) { index, new ->
```

**Impacto.** `collectResult` -> `collectResultAndRunActions` already fires `recipeResult.runActions(context, 1)` (RecipeUtils.kt:41) or `runActions(context, maxPossible)` (RecipeUtils.kt:49). `CustomRecipeCraftingImpl.shrink` then fires `result.runActions(context, count)` as its very first statement. Every custom crafting recipe therefore executes its result actions twice: a recipe whose action grants money, runs a console command or plays a sound pays out double on every craft. CrafterListener has the same double call in its own body: `recipe.shrink(...)` at line 82 followed by an explicit `recipe.result.runActions(context)` at line 85.

**Arreglo.** Drop the `runActions` call from the listener path (let `shrink` own it) or pass a flag so only one of the two executes; apply the same to CrafterListener.kt:85.

> **Verificación adversarial — confirmado.** Verified the double call: CraftingListener.kt:85 calls collectResult -> collectResultAndRunActions, which runs recipeResult.runActions at RecipeUtils.kt:41 (non-shift) or :49 (shift); then CraftingListener.kt:89 calls recipe.shrink, and CustomRecipeCraftingImpl.kt:36 begins with result.runActions(context, count). RecipeResultImpl.kt:25-33 runs every non-bulk action unconditionally regardless of count, so the actions fire twice per craft (and once even when count == 0). CrafterListener.kt:82 + :85 is the same pattern. Confirmed.

#### 🟡 BUKKIT-15 — Campfire: hand stack can go negative and the off-hand interaction is not filtered out

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/CampfireListener.kt:86`

```
val ingredientAmount = data.data.bySlot(0)?.matchedItemStackRef?.amount ?: 1

val toPlace = stack.clone().apply {
    amount = ingredientAmount
}

stack.amount -= ingredientAmount
```

**Impacto.** `IngredientImpl.match` only rejects empty stacks (`stack.amount <= 0`); it never requires the held amount to be >= the ingredient amount - which is exactly why `possibleResultAmount` has to divide amounts elsewhere. So with a custom campfire recipe requiring 4 coal, a player holding 1 coal still matches: a 4-item stack is placed on the campfire while the held stack is set to `1 - 4 = -3`, creating 3 items out of nothing and corrupting the hand stack. Second issue: unlike CauldronListener.kt:16 this handler never checks `event.hand`, so PlayerInteractEvent fires again for the off-hand and a player holding the ingredient in both hands consumes from both and fills two campfire slots with one right-click.

**Arreglo.** Guard with `if (stack.amount < ingredientAmount) return` before placing, and add `if (event.hand != EquipmentSlot.HAND) return` at the top of the handler.

#### 🟡 BUKKIT-16 — Furnace XP counters are incremented before the cancel checks, crediting smelts that never happen

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/FurnaceListener.kt:85`

```
updateRecipeExperience(
    block,
    cache.bukkitRecipe,
    cache.recipeEvaluationResult.recipe.key
)
```

**Impacto.** `updateRecipeExperience` (which increments the stored usage count and calls `blockState.update()`) runs before the two `event.isCancelled = true` returns at lines 109 and 116. A furnace whose result slot holds an item that is not similar to the custom result - or that is already full - cancels the smelt but has already banked one usage. Vanilla resets `cookingProgress` and re-attempts every cook cycle, so an unattended furnace with a mismatched result slot and a fuel supply keeps inflating `customRecipesUsedKey`; when the player finally breaks the furnace, `onCollectExperience` pays out XP for dozens of smelts that produced nothing. Each attempt also costs a full tile-entity snapshot plus `BlockState.update()`.

**Arreglo.** Move the `updateRecipeExperience` call after the result-slot compatibility checks, so it only runs on the success path just before `recipeCache.invalidate(blockPos)`.

#### 🟡 BUKKIT-17 — FurnaceListener.recipeCache keeps an entry per block position that is only ever removed on a successful smelt

**Severidad:** MEDIO · **Categoría:** `memory-leak` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/FurnaceListener.kt:34`

```
private val recipeCache = Caffeine.newBuilder().build<ScafallBlockPos, CookingRecipeCache>()
```

**Impacto.** The cache has no size bound and no expiry. `onStartSmelt` inserts an entry for every furnace/blast furnace/smoker that begins a smelt (lines 59-70, including the null-recipe case), and the only removal is `recipeCache.invalidate(blockPos)` on the success path at line 143. A furnace that is broken, unloaded, has its fuel removed, or whose smelt is cancelled by lines 109/116 leaves its CookingRecipeCache - which strongly references a full RecipeEvaluationResult - alive for the lifetime of the server. On a large server every furnace position ever used accumulates, and entries for unloaded chunks are never reclaimed.

**Arreglo.** Bound the cache (`expireAfterAccess`/`maximumSize`) and invalidate on the cancel paths and on furnace break/unload.

#### 🟡 BUKKIT-18 — Full server-wide recipe scan on every PrepareSmithingEvent

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/SmithingListener.kt:66`

```
if (Bukkit.getRecipesFor(resultStack).any {
        customCrafting.server!!.recipeManager.isRecipeDisabled((it as Keyed).key.toScafall())
    }) {
```

**Impacto.** `Bukkit.getRecipesFor` iterates the entire server recipe list (vanilla ~1200 recipes plus every plugin and datapack recipe) and allocates a new List of matches. PrepareSmithingEvent fires on every slot change in the smithing table, so each item a player drags in or out triggers a full scan plus the `any{}` lambda and a Key conversion per candidate, on the main thread. A few players fiddling with smithing tables adds a measurable per-tick cost; it also runs before it is known whether a custom recipe is involved at all.

**Arreglo.** Precompute a set of disabled vanilla recipe keys by result material (or query `recipeManager` by key directly) instead of scanning all server recipes per event.

#### 🟡 BUKKIT-19 — Number-key / drop clicks on the crafting result slot put the item on the cursor instead of the hotbar

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/CraftingListener.kt:77`

```
if (event.isShiftClick || cursor.type == Material.AIR || cursor.amount + resultItem!!.amount <= cursor.maxStackSize) {
```

**Impacto.** The handler only distinguishes shift-click from 'everything else'. A NUMBER_KEY click (hotbar swap) or a DROP/CONTROL_DROP click on the result slot always has an empty cursor, so the condition passes, the event is cancelled at line 75 and `collectResultAndRunActions` takes the non-shift branch, which calls `event.view.setCursor(result)` (RecipeUtils.kt:37). The player presses '1' expecting the craft to go to hotbar slot 1, or presses Q expecting to drop it, and instead ends up with the item stuck on the cursor - which, combined with the cancelled event, desyncs against the client's expectation. Same for double-click.

**Arreglo.** Switch on `event.click`: handle NUMBER_KEY by writing into `event.view.bottomInventory.setItem(event.hotbarButton, result)`, handle DROP by dropping the item, and ignore click types that cannot collect a result.

#### 🟡 BUKKIT-20 — Grindstone quick-move from the player inventory can dump items into the result slot

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/GrindstoneListener.kt:213`

```
if (event.isShiftClick) {
    val remains = topInventory.addItem(currentItem!!)
    event.currentItem = remains.get(0)
    event.isCancelled = true
    return
}
```

**Impacto.** `Inventory.addItem` on a GrindstoneInventory has no notion of the result slot; it fills the first partial/empty slot, which is index 2 (the result) once slots 0 and 1 are occupied. Player with a custom grinding recipe in slots 0/1 shift-clicks a third item from his inventory: it is force-placed into the result slot. Clicking that slot afterwards enters `onCollectResult` with the still-cached recipe data, hands the shift-clicked item back and consumes the two input ingredients at lines 97-107 for nothing. The same line NPEs (`currentItem!!`) if `getCurrentItem()` is null while the cursor is not empty, since the guard at line 147 only returns when *both* are empty.

**Arreglo.** Place explicitly into slots 0/1 (`topInventory.setItem`) after verifying they are free, and null-check `currentItem` before dereferencing.

#### 🟡 BUKKIT-6 — Custom smithing result is computed but never assigned to the event, so the recipe produces nothing

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/spigotlike/src/main/kotlin/com/wolfyscript/customcrafting/spigotlike/recipes/SmithingListener.kt:91`

```
val endResult = recipe.result.compute(
    data,
    context, Random(getSmithingSeed(event.view.player as Player))
).unwrapSpigot()

if (baseStack == null) {
    event.result = null
    return
}

SmithingUtils.copyDataComponentsTo(baseStack.wrap(), endResult.wrap(), recipe.copyOptions!!)
return
```

**Impacto.** `endResult` is built, data components are copied into it, and then the function returns without ever doing `event.result = endResult`. The result slot therefore keeps whatever vanilla computed (usually null for a non-vanilla combination), so `onCollectResult` bails out at line 130 (`resultStack == null || AIR`). Every custom smithing recipe shows an empty result slot and cannot be crafted. Secondary hazard on the same line: `recipe.copyOptions!!` NPEs on the main thread inside PrepareSmithingEvent for any recipe deserialized without copyOptions.

**Arreglo.** Assign the computed stack: `event.result = endResult` after the component copy, and replace `copyOptions!!` with a null-safe default.

> **Verificación adversarial — severidad corregida a MEDIO.** SmithingListener.kt:91-102 is as quoted: endResult is computed, SmithingUtils.copyDataComponentsTo writes into it, and the function returns at 102 without ever assigning event.result, so the computed stack is discarded (real defect). But the claimed impact is wrong: RecipePlaceholders.kt:145-158 registers a real SmithingTransformRecipe placeholder into the MC recipe manager for every CustomRecipeSmithing (registered via CustomCraftingServerSpigotLike.kt:29), so vanilla fills the result slot and onCollectResult (line 129, inventory.result) hands that out. The recipe is craftable; what is broken is that the player gets the raw first result choice without the copied base components/modifiers. The copyOptions!! hazard is real (CustomRecipeSmithing.kt:41 declares it nullable and documents it as optional), but it only throws a logged listener exception. Downgraded to MEDIO.

#### ⚪ BUKKIT-21 — Spigot loader tags every Sentry report as the Paper platform and loads the configuration twice

**Severidad:** BAJO · **Categoría:** `config` · **Ubicación:** `core/spigot/src/main/kotlin/com/wolfyscript/customcrafting/spigot/SpigotLoaderPlugin.kt:79`

```
setupSentry(
    MinecraftServer.getServer().serverVersion,
    PlatformType.PAPER,
    File(dataFolder, "${Key.CUSTOMCRAFTING_NAMESPACE}/${DataManager.DATA_PATH}")
)
```

**Impacto.** Every crash report coming from a Spigot server is attributed to PlatformType.PAPER, which makes platform-specific triage in Sentry wrong (Spigot-only bugs will be filed as Paper bugs). Additionally `configurationManager.load()` runs twice at startup: once from `CustomCraftingSpigot.onInit()` (CustomCraftingSpigot.kt:43) and again from `SpigotLoaderPlugin.onLoad()` (line 87) - the same duplication exists in the Paper module (CustomCraftingPaper.kt:146 vs PaperLoaderPlugin.kt:191), so all config files are parsed from disk twice on every boot.

**Arreglo.** Pass `PlatformType.SPIGOT` here, and drop one of the two `configurationManager.load()` calls in both loaders.


### Plataforma Fabric — `core/fabric`

<details><summary>Cobertura declarada por el lector (46 ficheros)</summary>

Read every file under core/fabric/src (all 24 mixins in src/main/java/.../mixin, all 11 files in .../inject, the 3 root Kotlin classes CustomCraftingFabric/FabricMod/ServerFabric, the 4 proxy-recipe Kotlin files), plus core/fabric/build.gradle.kts, src/main/resources/fabric.mod.json, src/main/resources/customcrafting.mixins.json and src/main/java/README.md. Cross-checked the mixin list in customcrafting.mixins.json against the files on disk (24 vs 24, all present and all declared; the single declared entrypoint com.wolfyscript.customcrafting.fabric.CustomCraftingFabricMod exists). To confirm call-chain semantics I also opened, outside the slice: core/api/.../evaluation/EvaluationContextState.kt, .../ingredient/Ingredient.kt, IngredientImpl.kt, IngredientConsumerConsumeImpl.kt, IngredientConsumerReplaceImpl.kt, IngredientConsumerKeepImpl.kt, .../recipe/RecipeManagerCommon.kt, CustomRecipeSmithingImpl.kt, and core/spigotlike/.../SmithingListener.kt + GrindstoneListener.kt for the platform-divergence comparison. What I could NOT verify: the exact Minecraft version (sharedLibs comes from an external scafall build-tools catalog, not from gradle/libs.versions.toml), so I could not decompile the vanilla target methods to check every mixin target signature, local-variable name or early-return structure against the real bytecode. The findings below therefore avoid claims that depend on an unread vanilla method body, except where I say so explicitly (FABRIC-9). Scafall itself (com.wolfyscript.scafall) is an external dependency with no sources in this repo, so ItemStackWrappersKt.wrap / ScafallItemStack.unwrap aliasing semantics are inferred from how the repo's own code uses them, not read.

</details>

#### 🟠 FABRIC-1 — Grindstone never clears a stale resultInfo, so a later vanilla grind cancels vanilla onTake and duplicates the input items

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/GrindstoneMenuMixin.java:77`

```
var data = customcrafting.getServer().getRecipeManager().evaluateRecipesOfType(RecipeTypes.INSTANCE.getGrinding().resolveOrThrow(), input, context);
if (data == null || data.getRecipe().getValue() == null) {
    return;
}
ci.cancel();
... ((GrindstoneResultSlotsExt) getSlot(2)).setResultInfo(data);
```

**Impacto.** computeCustomRecipeResult() only ever *writes* resultInfo onto the result slot; it never resets it when no custom recipe matches. The sibling implementation for the anvil does exactly that reset (AnvilMenuMixin.java:59 `resultInfo = null;` as the first statement of createResult). Concrete path: player puts item A + item B into a grindstone, a custom grinding recipe R matches, GrindstoneResultSlotsMixin's resultInfo is set. The player then swaps item B for item C so R no longer matches -> createResult returns at line 78 without clearing, vanilla GrindstoneMenu.createResult runs and puts a normal grindstone output (disenchanted item + XP) in slot 2. The player takes it: GrindstoneResultSlotsMixin.takeCustomRecipeOutput (line 56) sees the stale resultInfo != null and calls ci.cancel() at line 59, which skips vanilla GrindstoneMenu$4.onTake entirely - the part that does repairSlots.setItem(0, EMPTY) / setItem(1, EMPTY). The mixin then only shrinks whatever slots R's *stale* IngredientData happens to cover (data.bySlot(0)/bySlot(1) may be null, or may be a Keep consumer that mutates nothing), and awards XP from the stale data.getYield()-data.getPenalty(). Net result: the player receives the vanilla grindstone output while the input items stay in the grindstone, repeatable -> item duplication.

**Arreglo.** Mirror AnvilMenuMixin: make the first statement of computeCustomRecipeResult clear the slot state, e.g. `((GrindstoneResultSlotsExt) getSlot(2)).setResultInfo(null);` before evaluating, so the early `return` at line 78 leaves no stale result behind.

> **Verificación adversarial — confirmado.** Re-read GrindstoneMenuMixin.java:61-91 and GrindstoneResultSlotsMixin.java:54-103. The quoted evidence is exact: computeCustomRecipeResult() has no `setResultInfo(null)` anywhere on the no-match path (it returns at :78 before any write), while the sibling AnvilMenuMixin.java:59 does `resultInfo = null;` as the first statement of its createResult hook. The only reset of the grindstone slot's resultInfo is GrindstoneResultSlotsMixin.java:102, reached only after a *custom* take. GrindstoneResultSlotsMixin.java:56-59 cancels vanilla GrindstoneMenu$4.onTake whenever the stale resultInfo is non-null, so repairSlots is never emptied; :80-100 only shrink via the stale IngredientData (and IngredientConsumerKeepImpl.kt:113-115 returns target unchanged, consuming nothing). I found no guard upstream: GrindstoneRepairSlot0/1Mixin only force mayPlace=true, and nothing else touches setResultInfo (the only caller is GrindstoneMenuMixin.java:88). Duplication path stands.

#### 🟠 FABRIC-2 — Furnace burn drops the computed custom result when the output slot holds a different item, but still consumes the input

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AbstractFurnaceBlockEntityMixin.java:85`

```
if (existingResultStack.isEmpty()) {
    items.set(2, resultStack);
} else if (ItemStack.isSameItemSameComponents(existingResultStack, resultStack)) {
    // TODO: Do we still need these checks? All of this should be checked by the canBurn method already
    ...
}

((RecipeResultCacheExt) entity).customcrafting$getRecipeResultStateCache().reset(...);
cookingRecipe.getResult().runActions(context, 1);
... items.set(0, newStack.unwrap());
```

**Impacto.** When existingResultStack is non-empty AND not the same item/components as resultStack, neither branch executes, so no output is written - but control falls straight through to lines 94-107, which reset the seed cache, run the recipe actions and shrink the source stack in slot 0. The input is destroyed and nothing is produced. The TODO's assumption that canBurn already covered this is wrong for randomised results, because the two sides compute the result with different RNGs: the proxy used by canBurn computes with `Random` (kotlin Random.Default) - CustomCookingRecipeProxy.kt:42 `recipe.result.compute(resultInfo, context, Random)` - while this method computes with the per-furnace seeded random (line 78-80 `.get(...).getRandom()`). Concrete path: a custom smelting recipe whose result has two choices (gold ingot / iron ingot). Output slot already holds a gold ingot. canBurn assembles with Random.Default, gets gold ingot, sees the same item and allows the burn; burn() then computes iron ingot from the cached seed, the else-if at line 85 is false, and the ore in slot 0 is consumed with no ingot produced. Repeats every cook cycle.

**Arreglo.** Make the overflow/mismatch case bail out the same way the count check at line 87-90 does: `else { return; }` after the isSameItemSameComponents branch (before the reset/runActions/shrink block), or better, make canBurn and burn compute the result from the same seeded random so the two can never disagree.

> **Verificación adversarial — confirmado.** Re-read AbstractFurnaceBlockEntityMixin.java:80-107. Quote is exact: the if/else-if at :83-92 has no else, and :94-107 (cache reset, runActions, items.set(0, shrunken)) execute unconditionally after it, so a non-empty, non-matching output slot destroys the input with no product. The RNG divergence is confirmed: CustomCookingRecipeProxy.kt:42 computes with kotlin `Random` (Default) inside assemble() used by canBurn, while the mixin at :78-80 uses the per-block-entity seeded Entry.random (RecipeResultState.kt:22-47), and RecipeResultImpl.kt:19 picks the output via `choices.all().random(random)` - different Random, different choice. Contexts also differ (proxy uses EvaluationContextState.current ?: of(null,null) at CustomCookingRecipeProxy.kt:40, mixin builds of(null,null,pos,entity) at :77), so even a non-random result can differ after modifier.modify (RecipeResultImpl.kt:21). Holds.

#### 🟠 FABRIC-3 — Anvil and grindstone discard the ItemStack returned by Ingredient.shrink, so 'replace' ingredients are never consumed

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AnvilMenuMixin.java:114`

```
var base = data.bySlot(BASE_SLOT);
if (base != null) {
    base.getSelectedIngredient().shrink(
        ItemStackWrappersKt.wrap(getSlot(BASE_SLOT).getItem()),
        1,
        base.getMatchedItemStackRef(),
        context,
        resultInfo
    );
}
```

**Impacto.** Ingredient.shrink is documented (core/api/.../ingredient/Ingredient.kt:63 "@return the updated/new ScafallItemStack after consumption") and its Replace implementation returns a brand-new stack without touching the target: core/api/.../ingredient/IngredientConsumerReplaceImpl.kt:17 `return replacement.create()`. Here (and identically at AnvilMenuMixin.java:128 for the addition slot, and GrindstoneResultSlotsMixin.java:82 and :93) the return value is thrown away and nothing is written back into the slot. Every other call site in this module does write it back - SmithingMenuMixin.java:121-128, AbstractFurnaceBlockEntityMixin.java:99-106, StonecutterResultSlotMixin.java:58-66, CampfireBlockEntityMixin.java:125-126 - and so does the Spigot side (core/spigotlike/.../SmithingListener.kt:185 returns the shrunken stack). Concrete path: a custom repairing recipe whose base ingredient uses the 'replace' consumer (e.g. base = filled bucket, replaced by an empty bucket). Because ci.cancel() at AnvilMenuMixin.java:102 also skips vanilla AnvilMenu.onTake (which is what normally empties inputSlots), the base item stays in slot 0 at full count and the replacement is never created. The player takes the repaired item and can take it again immediately -> unlimited duplication.

**Arreglo.** Assign the result and write it back, as SmithingMenuMixin.shrinkCustomIngredient already does: `getSlot(BASE_SLOT).set(base.getSelectedIngredient().shrink(...).unwrap());` (and the same for ADDITION_SLOT here and for slots 0/1 in GrindstoneResultSlotsMixin).

> **Verificación adversarial — confirmado.** Re-read AnvilMenuMixin.java:112-135 and GrindstoneResultSlotsMixin.java:80-100: in all four places the ScafallItemStack returned by Ingredient.shrink is discarded as an expression statement. Confirmed the semantics: IngredientImpl.kt:38-46 delegates to consumption.consume; IngredientConsumerConsumeImpl.kt:48-49 mutates the target in place (so Consume survives the discard) but IngredientConsumerReplaceImpl.kt:17 is `return replacement.create()` with zero mutation of target, and Ingredient.kt:63 documents the return as the updated stack. Confirmed the contrast call sites all assign back: SmithingMenuMixin.java shrinkCustomIngredient (`existing = ...shrink(...).unwrap(); inputSlots.setItem(index, existing);`), StonecutterResultSlotMixin.java:57-65, CampfireBlockEntityMixin.java:124-126, AbstractFurnaceBlockEntityMixin.java:99-106. And AnvilMenuMixin.java:102 / GrindstoneResultSlotsMixin.java:59 both ci.cancel() the vanilla onTake that would otherwise clear the input slots, so nothing else empties them. Holds; the grindstone half is reachable with no extra preconditions.

#### 🟡 FABRIC-10 — Every furnace block entity eagerly allocates a 60-element recipe-seed cache it will almost never use

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AbstractFurnaceBlockEntityMixin.java:34`

```
@Unique
private final RecipeResultStateCache resultStateCache = new RecipeResultStateCache();
```

**Impacto.** Mixin merges this @Unique final initializer into every AbstractFurnaceBlockEntity constructor, so it runs for every furnace, blast furnace and smoker that is loaded from disk. RecipeResultState.kt:19-20 allocates eagerly per instance: `private val persistentCache = mutableMapOf<Key, Entry>()` plus `private val tempCache = ArrayList<Entry>(capacity)` with capacity = 60 - ArrayList(60) immediately allocates Object[60]. That is roughly 330-350 bytes of permanently retained heap per furnace block entity (cache object + LinkedHashMap + ArrayList + its backing array), on top of the block entity itself, and the cache is only ever read inside wrapShrinkItemAndProduceResult (line 78) when a *custom* cooking recipe actually burns - and even then it is reset one line later (line 94), so the cached seed is thrown away each craft. Concrete cost: a survival server with ~20,000 loaded furnace-family block entities (common on a large map with many loaded chunks) retains ~7 MB of arrays that are never written to, for a feature the vast majority of those furnaces never touch.

**Arreglo.** Make the field lazy (`by lazy { RecipeResultStateCache() }` or null until first use in customcrafting$getRecipeResultStateCache), and make RecipeResultStateCache.tempCache grow on demand (`ArrayList()` instead of `ArrayList(capacity)`) since capacity is an eviction bound, not an expected size.

#### 🟡 FABRIC-11 — Recipe-priority comparator formats a debug log line on every comparison during RecipeMap.create

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/RecipeMapMixin.java:57`

```
CustomCraftingProvider.Companion.get().getLogger().debug("Sort {} ({}) <> {} ({})", that.id().identifier(), thatPriority, other.id().identifier(), otherPriority);
return -1 * Integer.compare(thatPriority, otherPriority);
```

**Impacto.** This sits inside the Comparator handed to ImmutableMultimap.Builder.orderValuesBy, so it runs O(n log n) times over every recipe in the game when RecipeMap.create is invoked - which this module triggers on server start (RecipeManagerCustomRecipeMixin.java:60) and on every datapack reload (line 50). SLF4J has no 4-argument debug overload, so this resolves to debug(String, Object...) and allocates an Object[4] on every single comparison; both `that.id().identifier()` calls and the two int boxings are evaluated eagerly even when debug logging is off. With ~1,300 vanilla crafting recipes that is on the order of 10,000+ comparisons, each allocating an array plus two Identifier lookups and two Integer boxes, purely to produce log output that is then discarded.

**Arreglo.** Wrap the call in `if (logger.isDebugEnabled())`, or simply delete it - the comparator is on a reload hot path. Also `-1 * Integer.compare(a, b)` is just `Integer.compare(b, a)`.

#### 🟡 FABRIC-4 — Anvil onTake casts Player to ServerPlayer unconditionally on a code path the client also executes

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AnvilMenuMixin.java:137`

```
RecipeResultStateKt.resetRecipeResult((ServerPlayer) player, resultInfo.getRecipe().getKey());
```

**Impacto.** AnvilMenu.createResult() runs on the client too: ItemCombinerMenu's inputSlots is a SimpleContainer whose setChanged() calls slotsChanged() -> createResult(), and that fires both when the server syncs a slot (AbstractContainerMenu.setItem -> Slot.set -> setChanged) and during the client's local doClick prediction. customRecipeLogic (line 57) has no side check, so on a client it evaluates recipes and sets `resultInfo` - and in singleplayer CustomCraftingProvider.get().getServer() is non-null because the integrated server has started, so line 65 succeeds. The player then clicks the anvil output slot; MultiPlayerGameMode.handleInventoryMouseClick calls menu.clicked(...) -> doClick -> onTake with the LocalPlayer, the mixin's `resultInfo == null` guard at line 96 does not trip, and line 137 throws ClassCastException (LocalPlayer cannot be cast to ServerPlayer) inside inventory click handling -> client crash. The same file already knows about this: getResultRandom at line 80 guards with `if (player instanceof ServerPlayer serverPlayer)`, and line 142 guards the text-filter call the same way. fabric.mod.json declares "environment": "*", so the mod does load on the client.

**Arreglo.** Guard line 137 the way line 80 and line 142 already do: `if (player instanceof ServerPlayer serverPlayer) RecipeResultStateKt.resetRecipeResult(serverPlayer, ...)`. Better still, make customRecipeLogic return early when `!(player instanceof ServerPlayer)` so no client-side evaluation happens at all.

> **Verificación adversarial — severidad corregida a MEDIO.** The code claim is exact: AnvilMenuMixin.java:137 is an unguarded `(ServerPlayer) player` cast, while the same file guards at :80 (`player instanceof ServerPlayer serverPlayer`) and :142, and StonecutterResultSlotMixin.java:50 repeats the same unguarded cast. The side-independence is real too: customcrafting.mixins.json lists AnvilMenuMixin under the common "mixins" array (the "server" array is empty) and fabric.mod.json:14 is `"environment": "*"`, so the mixin applies on clients; customRecipeLogic (:57-76) has no side check and sets resultInfo unconditionally, and in singleplayer CustomCraftingProvider.get().server is non-null (CustomCraftingFabricMod.kt:45-56 initialises it on SERVER_STARTING, which the integrated server fires). What the reporter did not account for is reachability of onTake on the client: the custom path never assigns `cost` (the only write is :139, RESET_COST = 0), and vanilla AnvilMenu.mayPickup gates on cost.get() > 0, so doClick normally refuses to take the custom result at all - onTake is only reached when a stale non-zero cost survives from an earlier vanilla createResult pass (e.g. after a rename). Combined with this being singleplayer-only, the defect is real but its severity is MEDIO, not ALTO.

#### 🟡 FABRIC-5 — Proxy recipes are built inside RecipeManager.prepare(), which the datapack reload runs on a background worker thread

**Severidad:** MEDIO · **Categoría:** `concurrency` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/RecipeManagerCustomRecipeMixin.java:48`

```
@Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;", at = @At("RETURN"), cancellable = true)
private void registerCustomRecipeProxies(...) {
    ...
    List<RecipeHolder<?>> newList = new ArrayList<>(current.values());
    newList.addAll(RecipeRegistrationUtils.INSTANCE.registerProxyRecipes());
    cir.setReturnValue(RecipeMap.create(newList));
```

**Impacto.** The `prepare(ResourceManager, ProfilerFiller) -> RecipeMap` shape is the SimplePreparableReloadListener prepare phase, which vanilla schedules with CompletableFuture.supplyAsync on the background reload executor, not on the server thread. RecipeRegistrationUtils.registerProxyRecipes() (RecipeRegistrationUtils.kt:26-39) then iterates `customCrafting.server.recipeManager.recipes()`, which reads RecipeManagerCommon's `private var index: RecipeIndex` (core/api/.../RecipeManagerCommon.kt:30) - a plain, non-volatile field that the main thread reassigns in registerOrUpdateRecipes/removeRecipes (lines 181-187). It also constructs live Minecraft objects off-thread (Ingredient.of(...), stack.create().unwrap(), toShapedRecipePattern()). Concrete path: an operator runs /reload (or any datapack reload) while a CustomCrafting recipe reload is finishing on the main thread - the worker thread can observe a stale or half-published RecipeIndex and produce a RecipeMap with missing or removed proxy recipes, and because the same field `this.recipes` is also rewritten from the main thread by registerProxyRecipes() (line 60) there is no ordering guarantee between the two writes.

**Arreglo.** Do the proxy construction in the apply phase (which vanilla runs on the game executor) rather than in prepare, e.g. inject into RecipeManager.apply / finalizeRecipeLoading, or hand the work to the server thread via minecraftServer.execute(...) and join before returning.

> **Verificación adversarial — severidad corregida a MEDIO.** The mechanical facts check out: RecipeManagerCustomRecipeMixin.java:38-51 injects at RETURN of `prepare(ResourceManager, ProfilerFiller)RecipeMap`, which is the SimplePreparableReloadListener prepare phase run via CompletableFuture.supplyAsync on the background reload executor, and RecipeRegistrationUtils.kt:26-39 reads `customCrafting.server?.recipeManager?.recipes()` which returns the non-volatile `private var index: RecipeIndex` (RecipeManagerCommon.kt:30, reassigned at registerOrUpdateRecipes/removeRecipes, `override fun recipes() = index.values()`). However the reporter's concrete path is partly wrong: the sibling write to `this.recipes` in registerProxyRecipes() (RecipeManagerCustomRecipeMixin.java:60) is only invoked once, from CustomCraftingServerFabric.kt:35 at server load, not during reloads, so the claimed write-ordering conflict between the two does not arise on /reload. The index race does exist (and is in fact wider: RecipesCommand.kt:72 already runs loadResources on an async scheduler thread), but it is a latent visibility hazard with no deterministic failure and requires overlapping reloads. Also, building Ingredients/ItemStacks in the prepare phase is what vanilla recipe deserialization does anyway, so that half of the impact claim is not a defect. Downgrade to MEDIO.

#### 🟡 FABRIC-6 — EvaluationContextState is entered/exited from menu code that runs on the client thread, and CrafterMenuMixin exits without having entered

**Severidad:** MEDIO · **Categoría:** `concurrency` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/CrafterMenuMixin.java:38`

```
@Inject(at = @At("HEAD"), method = "refreshRecipeResult")
private void enterEvalContext(CallbackInfo ci) {
    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) { ... EvaluationContextState.INSTANCE.enter(...); }
}

@Inject(at = @At("RETURN"), method = "refreshRecipeResult")
private void exitEvalContext(CallbackInfo ci) {
    EvaluationContextState.INSTANCE.exit();
}
```

**Impacto.** EvaluationContextState (core/api/.../evaluation/EvaluationContextState.kt:5) is `object { private var context: EvaluationContext? }` - one global, non-ThreadLocal, non-stack slot; exit() unconditionally assigns null. Two defects follow from how this module drives it. (a) The enter here is guarded by `instanceof ServerPlayer` but the exit is not, so every client-side refreshRecipeResult (called from CrafterMenu.slotsChanged, which the client runs on slot sync and on local doClick prediction) nulls the global context without ever having set it. (b) SmithingMenuMixin.java:75-85 and AnvilMenuMixin/GrindstoneMenuMixin's createResult hooks likewise run on the client render thread (ItemCombinerMenu.inputSlots.setChanged -> slotsChanged -> createResult) while, in singleplayer, the integrated server thread is simultaneously inside AbstractFurnaceBlockEntityMixin.serverTick between its enter (line 43) and exit (line 48). Concrete path: singleplayer, a furnace is mid-tick running a custom cooking recipe that has a position/block-entity condition; the player moves an item in an open smithing table or crafter GUI on the render thread; the furnace's recipe match then reads EvaluationContextState.current and gets either null (CustomCookingRecipeProxy.kt:24 falls back to EvaluationContext.of(null, null)) or the player's menu context, so a position-conditioned recipe silently stops matching or starts matching where it should not.

**Arreglo.** Make the exit symmetric with the enter (guard it with the same `instanceof ServerPlayer` check), and have every mixin that touches EvaluationContextState bail out when the level is not a ServerLevel. The underlying fix belongs in core/api: make the state a ThreadLocal holding a stack, so enter/exit nest and cannot cross threads.

> **Verificación adversarial — severidad corregida a MEDIO.** Verified all three pieces. EvaluationContextState.kt:3-14 is exactly `object` with a single plain `private var context: EvaluationContext?`, no ThreadLocal, no stack, and exit() unconditionally nulls it. CrafterMenuMixin.java:29-41 matches the quote: enter is inside `if (player instanceof ServerPlayer)` while exit at :38-41 is unconditional, so a client-side refreshRecipeResult nulls the shared global without ever having entered. SmithingMenuMixin enterEvalContext/exitEvalContext are injected at HEAD/TAIL of createResult with no side check, and customcrafting.mixins.json puts every one of these in the common "mixins" list with fabric.mod.json `"environment": "*"`, so they do run on the client render thread while AbstractFurnaceBlockEntityMixin.java:41-49 has the integrated-server thread between its own enter/exit. The consequence chain is real: CustomCookingRecipeProxy.kt:24 reads `EvaluationContextState.current ?: EvaluationContext.of(null, null)`, so a clobbered global changes whether a position/block-entity-conditioned recipe matches. Severity reduced to MEDIO: on a dedicated server CrafterMenu's player is always a ServerPlayer so the unbalanced exit never fires, and the cross-thread clobber requires singleplayer plus concurrent GUI interaction, producing a transient mismatch rather than a persistent corruption.

#### 🟡 FABRIC-7 — Custom anvil recipes charge the stale level cost left over from the last vanilla anvil computation

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AnvilMenuMixin.java:69`

```
ci.cancel(); // Return before vanilla logic

resultInfo = data;
var recipe = data.getRecipe().getValue();
var result = recipe.getProcess().compute(data, input, context, getResultRandom(player, resultInfo.getRecipe()));

getSlot(getResultSlot()).set(result.unwrap());
```

**Impacto.** customRecipeLogic cancels vanilla createResult and never assigns the shadowed `cost` DataSlot (the only write to it in this file is `cost.set(RESET_COST)` at line 139, after a take). So when a custom repairing recipe matches, `cost` still holds whatever vanilla's last run put there. Concrete path: player puts two damaged diamond swords in the anvil, vanilla computes cost = 7 and the client shows "Enchantment Cost: 7". The player swaps in the ingredients of a custom repairing recipe; the mixin cancels vanilla, so cost stays 7. The anvil UI still advertises 7 levels, and onTakeCustomRecipeOutput line 104-106 executes `player.giveExperienceLevels(-cost.get())`, deducting 7 levels the custom recipe never asked for. Symmetrically, if the previous vanilla state left cost = 0 the custom recipe is free and, for a non-creative player, the vanilla client renders the result slot as unusable.

**Arreglo.** Have customRecipeLogic set `cost` explicitly from the custom recipe's configured cost (or to 0 when it has none) before writing the result slot, and broadcast the change, instead of leaving the DataSlot at its stale vanilla value.

#### 🟡 FABRIC-8 — Commands are registered only for dedicated servers, so the mod has no commands in singleplayer or Open-to-LAN

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/kotlin/com/wolfyscript/customcrafting/fabric/CustomCraftingFabricMod.kt:70`

```
CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, env ->
    if (env.includeDedicated) {
        logger.info("Registering CustomCraftingFabricMod commands")
        CCCommands.registerCommands(dispatcher)
    }
}
```

**Impacto.** MinecraftServer builds its Commands with CommandSelection.INTEGRATED when isDedicatedServer() is false, and INTEGRATED has includeDedicated = false. Meanwhile fabric.mod.json declares "environment": "*" and onInitialize registers SERVER_STARTING/SERVER_STARTED for any server, so on an integrated server the mod loads, initServer() runs, configuration and resources load, proxy recipes are registered - and then not a single CustomCrafting command exists. Concrete path: a user installs the mod, opens a singleplayer world (or Open to LAN), types /customcrafting or /cc and gets "Unknown command", with no way to reload or manage recipes in the only environment where the mod's own logs say it started fine.

**Arreglo.** Register whenever the selection includes any server: drop the condition, or use `if (env.includeDedicated || env.includeIntegrated)`. If commands are genuinely meant to be dedicated-only, fabric.mod.json should say "environment": "server" so the mod does not half-load in singleplayer.

#### 🟡 FABRIC-9 — Mixins capture locals by source name (@Local(name=...) / @ModifyVariable(name=...)), which cannot resolve against a production Minecraft jar

**Severidad:** MEDIO · **Categoría:** `api-misuse` · **Ubicación:** `core/fabric/src/main/java/com/wolfyscript/customcrafting/fabric/mixin/AbstractFurnaceBlockEntityMixin.java:51`

```
@ModifyVariable(method = "serverTick", at = @At(value = "STORE"), name = "input")
...
@WrapOperation(... ) private static void wrapShrinkItemAndProduceResult(..., @Local(name = "input") SingleRecipeInput input)
```

**Impacto.** Name-based local matching needs a LocalVariableTable in the class being transformed. Minecraft's shipped jars are ProGuard-obfuscated with locals stripped, so these names exist in the dev (named-mappings) workspace but not in the jar the mod is mixed into at runtime. The same pattern is used at CampfireBlockEntityMixin.java:132 (@Local(name = "input")), CrafterMixin.java:54 (@Local(name = "blockEntity"), @Local(name = "craftInput")) and CraftingResultSlotMixin.java:48 (@Local(name = "input")). Concrete path: the mod builds and runs fine in runServer from Loom, then on a real installed server MixinExtras cannot find a local called "input" in AbstractFurnaceBlockEntity.serverTick; with "injectors": {"defaultRequire": 1} in customcrafting.mixins.json this is a hard mixin apply failure at class-load, taking the server down at startup rather than degrading. I did not decompile the target methods (the Minecraft version comes from an external scafall catalog, not from this repo), so I cannot show the concrete name mismatch - the defect is the reliance on names that are absent from the production target at all.

**Arreglo.** Replace `name = "..."` with positional matching - `@Local(ordinal = n)` or `@Local(index = n)` for MixinExtras, and `ordinal` for @ModifyVariable - which resolve off the bytecode's slot/type layout and survive obfuscation. Keep the names only in a comment.

#### ⚪ FABRIC-12 — The non-persistent seed cache is documented as capacity-bounded reuse but never refreshes a hit, so it evicts by insertion order

**Severidad:** BAJO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/kotlin/com/wolfyscript/customcrafting/fabric/inject/RecipeResultState.kt:27`

```
var found = tempCache.find { it.recipeKey == recipeKey } // Simply use a linear search, since size is limited and small.
if (found == null) {
    found = Entry(recipeKey, Random.nextLong())
    if (tempCache.size >= capacity) {
        tempCache.removeLast()
    }
    tempCache.add(0, found)
}
return found
```

**Impacto.** The class doc says the temporary cache rerolls "when the number of recipes crafted exceeds the capacity", i.e. least-recently-used eviction, and the add(0, ...) / removeLast() pair is written as an LRU. But a cache *hit* leaves the entry where it is, so the list is ordered by first insertion, not by last use. Concrete path: a player repeatedly uses one non-persistent custom recipe A while occasionally trying 60 other recipes; A's entry drifts to the tail purely because it was inserted early and is evicted by removeLast() even though it is the most-used one, so A's displayed result silently rerolls mid-session while a recipe used once stays stable.

**Arreglo.** On a hit, move the entry to the front: `tempCache.remove(found); tempCache.add(0, found)` (or use a LinkedHashMap with accessOrder = true and removeEldestEntry, which gives real LRU without the linear scan).

#### ⚪ FABRIC-13 — Shutdown handler dereferences the lateinit customCrafting without the whenReady guard used everywhere else

**Severidad:** BAJO · **Categoría:** `bug` · **Ubicación:** `core/fabric/src/main/kotlin/com/wolfyscript/customcrafting/fabric/CustomCraftingFabricMod.kt:66`

```
ServerLifecycleEvents.SERVER_STOPPED.register {

    customCrafting.server?.onUnload()
}
```

**Impacto.** `customCrafting` is `private lateinit var` (line 27) and is only assigned inside `ScafallProvider.whenReady { ... }` (lines 36-40), which the comment at line 36 says is needed precisely because "Load order isn't deterministic". The SERVER_STARTING handler respects that and wraps its access in ScafallProvider.whenReady (line 48); this one does not. Concrete path: scafall fails to become ready, or the server aborts during startup before whenReady fires (bad config, port in use, world load failure). SERVER_STOPPED still fires, and line 66 throws UninitializedPropertyAccessException from inside the shutdown hook, masking the real startup error in the log and interrupting the rest of the shutdown sequence.

**Arreglo.** Guard it the same way the SERVER_STARTING handler does - wrap in ScafallProvider.whenReady, or declare the field as `private var customCrafting: CustomCraftingFabric? = null` and use `customCrafting?.server?.onUnload()`.


### Editor: dominio, casos de uso y CLI — `editor/common`

<details><summary>Cobertura declarada por el lector (40 ficheros)</summary>

Read every .kt file under editor/common/src (52 files: EditorModule/Impl, EditorRegistries, EditorRegistryTypes, EditorServer, EditorSession/Impl, SessionManager/Impl, ext/EditorModelFactory, cli/RecipeEditorCLI, cli/commands/RecipesEditorCommand, domain/SessionModel, domain/model/SessionStateImpl, all of domain/model/recipe/** and domain/model/recipe/item/**, all three usecase files). To verify call chains and reachability I also opened, outside the slice: core/api CraftingFormula.kt, CraftingFormulaShapedImpl.kt, CustomRecipeCrafting.kt, RecipeResult.kt, ingredient/Ingredient.kt, resource/ResourceLoaderImpl.kt, resource/DirectorySource.kt, core/common commands/RecipesCommand.kt, and ui/common AddIngredientsPage.kt, FormulaPage.kt, IngredientEditor.kt (only to prove reachability; no findings reported against them). Not covered: I could not open the scafall dependency sources (Key, ItemStackRef, Registry, ValueReference), so I did not write up anything that depends on scafall's validation behaviour - notably whether `Key.customCrafting(name)` rejects the uppercase/`+`/`.` characters that brigadier's `StringArgumentType.word()` lets through in `/recipes editor save <name>` (a potential uncaught-throw in the command executor that I deliberately left out rather than guess), and whether `ItemStackRef.parse(ItemStack.EMPTY.wrap())!!` in IngredientConsumerModels.kt:26 can return null (that `!!` is currently unreachable anyway - no UI path calls IngredientConsumerReplaceModelFactory.createEmptyModel()). I also did not audit the conditions sub-package beyond reading it: ConditionModel/RecipeConditionsModel are interfaces with no implementation in the repo, and the conditions/resultActions/transmuters registries are registered empty.

</details>

#### 🟠 EDITOR-1 — Shaped crafting formula refuses to complete unless all 9 grid slots are filled, so no normal shaped recipe can ever be saved

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeCraftingModelImpl.kt:177`

```
for (ref in ingredientRefs) {
    val resolvedIngredient = ref?.resolveFor(collection)
        ?: return Result.failure(IllegalStateException("Failed to resolve ingredient for $ref: Missing ingredient in collection"))
    val shapeId = ref.toShapeId()
```

**Impacto.** `ingredientRefs` is fixed at 9 entries initialised to null (line 153: `arrayOfNulls<IngredientModelRef?>(9).toMutableList()`) and `assignIngredient` only ever writes into existing indices, so an unassigned grid slot stays null. The loop dereferences every one of the 9 entries, so any null aborts with a failure. Concrete path: player runs `/recipes create customcrafting:crafting`, opens FormulaPageAdvanced, assigns 4 ingredients for a 2x2 recipe (grid slots 0,1,3,4), then `/recipes editor save my_recipe`. `RecipeCraftingModelImpl.complete()` -> `formula.complete()` returns failure "Failed to resolve ingredient for null"; `CreateRecipeSessionModel.save` logs it and returns, nothing is written to disk, and the CLI still prints "Saved recipe under my_recipe" (see EDITOR-9). Only a fully populated 3x3 recipe can be saved. The sibling `ShapeModel.complete()` at line 222 explicitly maps a null ref to `' '`, and core's `CraftingFormulaShapedImpl.ShapeImpl` init treats `column.isWhitespace()` as an empty slot (index -1), so empty slots are unambiguously the intended design.

**Arreglo.** Skip unassigned slots instead of failing: `for (ref in ingredientRefs) { if (ref == null) continue; val resolved = ref.resolveFor(collection) ?: return Result.failure(...) ; ... }`. The existing `if (mappedIngredients.isEmpty())` check at line 190 already covers the "nothing assigned at all" case.

> **Verificación adversarial — confirmado.** Re-read RecipeCraftingModelImpl.kt:151-189. Line 153 really is arrayOfNulls<IngredientModelRef?>(9).toMutableList(); assignIngredient (159-166) only writes existing indices and unassignIngredient (168-172) writes null back, so nulls persist. At 176-178 the elvis covers the whole safe-call (ref?.resolveFor(collection) ?: return Result.failure(...)), so a null ref short-circuits to failure; no filterNotNull or skip anywhere in the loop. Line 222 (rows[index/3] += ref?.toShapeId() ?: ' ') confirms blank slots are the intended representation. Caller chain verified: SessionStateImpl.kt:47-55 logs the failure and returns without writing, while RecipeEditorCLI.kt:40 unconditionally prints 'Saved recipe under ...'. Finding holds.

#### 🟠 EDITOR-2 — ShapelessCraftingFormulaModel.unassignIngredient bounds-checks the wrong value and throws IndexOutOfBoundsException

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeCraftingModelImpl.kt:121`

```
override fun unassignIngredient(index: Int) {
    if (index >= 0 && ingredientRefs.size < 9) {
        ingredientRefs.removeAt(index)
    }
}
```

**Impacto.** The guard checks `ingredientRefs.size < 9` (a capacity check) instead of `index < ingredientRefs.size`. Concrete path: player switches the formula to shapeless (FormulaPage.kt:119 `setFormulaType.set(CraftingFormula.Shapeless::class.java)`), assigns 2 ingredients, then scrolls grid slot 5 back to "reset" - FormulaPage.kt:221 calls `store.resetIngredientForSlot(5)` -> `UnassignIngredient.unassign(5)` -> `5 >= 0 && 2 < 9` is true -> `removeAt(5)` on a 2-element list -> IndexOutOfBoundsException thrown on the main server thread from inside the inventory-click handler, aborting the GUI update. Conversely, once the list holds 9 refs the guard is false and no ref can ever be removed.

**Arreglo.** Change the condition to `if (index >= 0 && index < ingredientRefs.size)`, matching the correct guard already used in `ShapedCraftingFormulaModel.unassignIngredient` at line 169.

> **Verificación adversarial — confirmado.** RecipeCraftingModelImpl.kt:120-124 is verbatim as quoted: the guard is 'index >= 0 && ingredientRefs.size < 9' then removeAt(index) - a capacity check, not index < size. Caller chain has no intermediate bound check: FormulaPage.kt:216-221 computes index = row*3+column (0..8) and calls store.resetIngredientForSlot(index); FormulaPage.kt:135-137 forwards to unassignIngredient.unassign(index); RecipeCraftingUseCases.kt:87-92 forwards straight to model.formula.unassignIngredient(index). The shapeless list starts empty (line 106 mutableListOf()), so removeAt(5) on a 2-element list throws IndexOutOfBoundsException, and at size 9 the guard blocks all removals. Finding holds.

#### 🟠 EDITOR-3 — ShapelessCraftingFormulaModel.assignIngredient off-by-one: slot 0 can never be assigned and appends land at the wrong index

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeCraftingModelImpl.kt:115`

```
if (index >= 0 && index < ingredientRefs.size) {
    ingredientRefs[index] = IngredientModelRefImpl(collectionIndex)
} else if (index > ingredientRefs.size && ingredientRefs.size < 9) {
    ingredientRefs.add(IngredientModelRefImpl(collectionIndex))
}
```

**Impacto.** `ShapelessCraftingFormulaModel` starts with an empty `ingredientRefs` list. The append branch tests `index > size` instead of `index >= size`, so the boundary case `index == size` falls through both branches and does nothing. Concrete path: player switches the formula to shapeless and scroll-selects an ingredient on the top-left grid button (FormulaPage.kt:219 passes `index = 0`): `0 < 0` false, `0 > 0` false -> no-op, the button appears to do nothing. After clicking slot 1 (which appends at list index 0) and slot 2 (appends at index 1), clicking slot 1 again now hits the first branch and *overwrites* the entry instead of adding, so the ingredient count silently stops growing. A user who only ever clicks grid slot 0 ends up with an empty list and `complete()` at line 142 fails with "Must have at least 1 ingredient".

**Arreglo.** Use `else if (index >= ingredientRefs.size && ingredientRefs.size < 9)`, or drop the positional overloading entirely for shapeless (position is meaningless there) and expose explicit add/remove operations.

> **Verificación adversarial — confirmado.** RecipeCraftingModelImpl.kt:113-117 matches the quote exactly, including 'else if (index > ingredientRefs.size && ingredientRefs.size < 9)'. With the shapeless default empty list (line 106) and index 0 coming from FormulaPage.kt:216-221 (index = row*3+column, forwarded unchecked through FormulaPage.kt:128-131 and RecipeCraftingUseCases.kt:80-85), 0 < 0 is false and 0 > 0 is false, so the call is a silent no-op; index == size is always skipped, so appends land off-by-one. Shapeless is reachable: FormulaPage.kt:115-121 toggleFormulaType calls setFormulaType.set(CraftingFormula.Shapeless::class.java) and RecipeCraftingModelImpl.kt:77-79 builds ShapelessCraftingFormulaModel. complete() at line 142-144 then fails with 'Must have at least 1 ingredient'. Finding holds.

#### 🟠 EDITOR-4 — Choices use-cases rebuild the ingredient with only 2 constructor args, silently resetting the configured matcher and consumer to defaults

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/usecase/IngredientUseCases.kt:70`

```
val updated = CustomIngredientModelImpl(
    ingredient.replaceWithRemains,
    RecipeChoicesModelImpl(stacks, ingredient.choices.tags)
)
setIngredientUseCase.set(ingredientIndex, updated)
```

**Impacto.** `CustomIngredientModelImpl`'s 3rd and 4th parameters default to `IngredientMatcherModels.exact...createEmptyModel()` and `IngredientConsumerModels.consume...createEmptyModel()` (IngredientModelImpl.kt:15-16), so omitting them discards whatever the user configured. Concrete path: in `IngredientEditor`, the player opens SubMenu.MATCHER and picks the "item" matcher with mustContain tags (IngredientEditor.kt:190 `store.setMatcher(index, matcher)` -> `Matcher.Set`, which correctly preserves all four fields), then switches to SubMenu.STACK_CHOICES in the *same* menu and adds or replaces one item stack (IngredientEditor.kt:72-73 -> `Choices.Add.add` / `Choices.Set.set`). The ingredient is rebuilt with the default Exact matcher and Consume consumer, so the matcher config is gone with no feedback. The same defect is in `Choices.Remove.remove` (line 89) and `Choices.Set.set` (line 109); `Tags.Add`/`Tags.Remove` (lines 148-153, 169-174) do pass `ingredient.matcher, ingredient.consumer`, which shows the omission is accidental.

**Arreglo.** Pass `ingredient.matcher, ingredient.consumer` in all three `Choices` use-cases, exactly as the `Tags` use-cases do. Better still: give `IngredientModel.CustomIngredientModel` a `copy(choices = ...)`-style helper so no call site can forget a field.

> **Verificación adversarial — confirmado.** IngredientUseCases.kt lines 70, 89 and 109 (Choices.Add/Remove/Set) each construct CustomIngredientModelImpl with only replaceWithRemains and a new RecipeChoicesModelImpl, while lines 148, 169 and 184 (Tags.Add/Remove x2), 220 (Matcher.Set) and 256 (Consumer.Set) all pass ingredient.matcher and ingredient.consumer - confirming the omission is accidental and not a convention. IngredientModelImpl.kt:15-16 confirms params 3 and 4 default to IngredientMatcherModels.exact...createEmptyModel() and IngredientConsumerModels.consume...createEmptyModel(), so the configured matcher/consumer is silently replaced. Both use-case groups are wired into the same store (IngredientEditor.kt:36-43 and 134-135; AddIngredientsPage.kt:119-121), so the interleaved matcher-then-choices path is real. Finding holds.

#### 🟠 EDITOR-6 — A player can create exactly one recipe per server uptime: create() refuses while a model exists and nothing ever clears it

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/EditorSessionImpl.kt:31`

```
if (model != null) {
    return Result.failure(IllegalStateException("Already editing a recipe of type ${model!!.recipeModel.recipeType}. Cancel and try again."))
}
```

**Impacto.** `model` is only ever cleared in `EditorSessionImpl.cancel()` (line 58-61), and `cancel()` has no caller in the repo - the CLI tree in RecipeEditorCLI.kt exposes only `editor save`, `create` and `edit`, with no `cancel` subcommand, and saving does not reset the model either (`CreateRecipeSessionModel.save` in SessionStateImpl.kt:47-55 leaves the session untouched). Concrete path: player runs `/recipes create customcrafting:crafting`, builds and saves a recipe, then runs `/recipes create customcrafting:crafting` again -> the command prints "Failed to create ... recipe: Already editing a recipe of type ..." and, because the session is also never deleted (EDITOR-5), that player can never create another recipe until the server restarts. The error message tells them to "Cancel and try again", but no cancel command exists.

**Arreglo.** Add a `/recipes editor cancel` subcommand wired to `session.cancel()`, and clear `model` (or delete the session) after a successful save in `CreateRecipeSessionModel.save`/`EditRecipeSessionModel.saveAs`.

> **Verificación adversarial — confirmado.** EditorSessionImpl.kt:30-32 matches the quote: create() returns failure whenever model != null. model is assigned only at lines 26 and 37 and cleared only at line 60 inside cancel(), which has no caller (grep for cancel across editor/ and ui/ yields only the declarations at EditorSession.kt:34, SessionModel.kt:10, SessionStateImpl.kt:38/57 - all empty bodies - and EditorSessionImpl.kt:58). SessionStateImpl.kt:47-55 confirms save() does not touch the session. RecipeEditorCLI.kt:20-91 exposes only 'editor save', 'create' and 'edit' - no cancel subcommand - and line 65 prints the failure message that tells the user to cancel. Combined with EDITOR-5 (session never removed), a player is locked out of creating a second recipe for the rest of the uptime. Finding holds.

#### 🟡 EDITOR-10 — Editing an existing recipe loads a blank model and silently replaces any in-progress work

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeCraftingModelImpl.kt:19`

```
override fun edit(recipe: CustomRecipeCrafting): RecipeModel.RecipeTypeSpecificModel<CustomRecipeCrafting> {
    return RecipeCraftingModelImpl() // TODO: load recipe into state
}
```

**Impacto.** `RecipeModel.RecipeTypeSpecificModel.Factory.edit` is documented (RecipeModel.kt:52-55) as loading the recipe with a full clone, but the only registered implementation ignores its `recipe` argument and returns an empty model. `EditorSessionImpl.edit` (line 21-28) then wraps it in `EditRecipeSessionModel(recipeKey, store)` and assigns `model = ...` with no `if (model != null)` guard - unlike `create()` at line 31, which does guard. So calling `edit` on a session that already holds unsaved work silently discards that work and replaces it with a blank model pointing at an existing recipe key. `EditRecipeSessionModel.save()` targets that existing key, so a user who edits and saves writes an empty recipe over their existing one; today the only thing preventing the overwrite is that `complete()` happens to fail on the empty formula (EDITOR-1), which is accidental protection, not validation.

**Arreglo.** Implement `RecipeCraftingModelFactory.edit` to deep-copy the recipe's formula, ingredients, result and conditions into the model, and add the same `if (model != null) return Result.failure(...)` guard to `EditorSessionImpl.edit` that `create` already has.

#### 🟡 EDITOR-11 — Removing an ingredient from the collection shifts indices while formula refs keep stale positional indices

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeCraftingModelImpl.kt:49`

```
override fun remove(index: Int) {
    ingredients.removeAt(index)
}
```

**Impacto.** `IngredientModelRefImpl` stores a bare positional index and resolves with `collection.ingredients.getOrNull(indexInCollection)` (IngredientModelRefImpl.kt:10), and `toShapeId()` derives the shape character from that same index (IngredientModelRef.kt:11). `remove` shifts every later element down by one without remapping the refs held by the formula. Concrete path: player adds ingredients A,B,C (indices 0,1,2), assigns grid slots to refs 1 and 2, then removes A. Ref 1 now resolves to C and ref 2 resolves to null, so the shaped formula either silently swaps an ingredient or fails to complete with "Missing ingredient in collection". The same list is also handed out raw as `val ingredients: MutableList<IngredientModel>` (RecipeCraftingModel.kt:25), so any caller can mutate it and break refs without going through `remove`. `remove` additionally has no bounds check while `add` does (`if (ingredients.size < 9)`), so an out-of-range index throws IndexOutOfBoundsException. Currently latent: `RemoveIngredientUseCase` is constructed and injected (ui/common/.../AddIngredientsPage.kt:118) and `AddIngredientStore.removeIngredient` exists at line 73, but no composable calls it yet - it is one wire-up away.

**Arreglo.** Either give ingredients stable identities (a generated id per ingredient that refs point at) instead of list positions, or have `remove(index)` walk the formula and drop/renumber every `IngredientModelRef` whose `indexInCollection` is >= the removed index. Add a bounds check too.

#### 🟡 EDITOR-12 — Six of the seven recipe-type factory references are copy-pasted with the wrong generic type and are not registered

**Severidad:** MEDIO · **Categoría:** `dead-code` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/RecipeTypeSpecificStateFactories.kt:15`

```
val crafting = create<CustomRecipeCrafting>("crafting")
val cooking = create<CustomRecipeCrafting>("cooking")
val mixing = create<CustomRecipeCrafting>("mixing")
val repairing = create<CustomRecipeCrafting>("repairing")
val smithing = create<CustomRecipeCrafting>("smithing")
val stonecutting = create<CustomRecipeCrafting>("stonecutting")
val grinding = create<CustomRecipeCrafting>("grinding")
```

**Impacto.** Every entry is typed `Factory<CustomRecipeCrafting>` even though the matching model interfaces exist and are typed correctly (`RecipeCookingModel : RecipeTypeSpecificModel<CustomRecipeCooking>`, `RecipeGrindingModel : ...<CustomRecipeGrinding>`, etc.). `EditorRegistries.initRegistries` (line 33-36) registers only `RecipeTypeSpecificStateFactories.crafting.key.key`, so the other six keys are absent from the registry. Any caller touching e.g. `RecipeTypeSpecificStateFactories.cooking.resolveOrThrow()` gets a throw today, and if a cooking factory is ever registered the declared type will produce a ClassCastException at the unchecked cast in `EditorSessionImpl.createTyped` (line 44). Meanwhile `/recipes create <type>` tab-completes every key in `CustomCraftingRegistryTypes.recipeTypes` (RecipeEditorCLI.kt:68-74), so a player can suggest-complete `cooking` and get the generic "Failed to create editor store: missing type factory" failure.

**Arreglo.** Correct each generic parameter to its own recipe type (`create<CustomRecipeCooking>("cooking")` etc.) or delete the six unimplemented entries until their factories exist, and filter the `/recipes create` suggestion list to recipe types that actually have a registered editor factory.

#### 🟡 EDITOR-13 — /recipes edit <recipe> has an empty executor body: it reports success and does nothing

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/cli/RecipeEditorCLI.kt:80`

```
Commands.literal("edit")
    .then(Commands.argument("recipe", IdentifierArgument.id()).executes { ctx ->

        return@executes SUCCESS_RESULT
    }.suggests { context, builder ->
```

**Impacto.** The subcommand is fully registered, permission-gated and has a working suggestion provider that lists every loaded recipe (lines 84-88), so it looks functional. A player runs `/recipes edit customcrafting:my_recipe`, gets no error and a success result code, and no session is created and `EditorSession.edit` is never called - the recipe editor for existing recipes is unreachable from the CLI. Note that `edit` and `create` are also attached to the `recipes` root rather than under the `editor` literal that `save` sits under (line 23 vs 45/78), so the actual command set is `/recipes editor save`, `/recipes create`, `/recipes edit` - inconsistent grouping.

**Arreglo.** Implement the executor (resolve the id, call `editor.getOrCreateSession(uuid).getOrThrow().edit(key)` and report the Result), or remove the subcommand until `RecipeCraftingModelFactory.edit` (EDITOR-10) is implemented. Move `create`/`edit` under the `editor` literal for a consistent tree.

#### 🟡 EDITOR-5 — Editor sessions are never removed: deleteSession() has no caller anywhere in the repo

**Severidad:** MEDIO · **Categoría:** `memory-leak` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/SessionManagerImpl.kt:7`

```
private val sessions: MutableMap<UUID, EditorSession> = mutableMapOf()
...
override fun deleteSession(uuid: UUID) {
    sessions.remove(uuid)
}
```

**Impacto.** `getOrCreateSession` is called from the UI store factories (AddIngredientsPage.kt:107, FormulaPage.kt:175) and from both CLI executors, but a grep across ui/, editor/ and core/ finds zero call sites for `deleteSession` and zero for `EditorSession.cancel()`/`SessionModel.cancel()`. Every UUID that ever opens the recipe editor leaves behind an `EditorSessionImpl` holding a full `SessionModel` -> `RecipeCraftingModelImpl` -> ingredient collection of `ItemStackRef`s and a `ResultModel`, retained for the whole server uptime even after the player disconnects. Growth is unbounded across restarts-free uptime and is never reclaimed. Secondary effect: the map is a plain `mutableMapOf()` (LinkedHashMap) but `getOrCreateSession` is reachable both from command execution and from GUI store construction, so it is not safe if either ever moves off the main thread.

**Arreglo.** Remove the session on player quit (a disconnect listener calling `deleteSession(uuid)`) and after a successful save/cancel. Consider `ConcurrentHashMap` plus `computeIfAbsent` in `getOrCreateSession`, which also fixes the check-then-act in lines 20-23.

> **Verificación adversarial — severidad corregida a MEDIO.** SessionManagerImpl.kt:7 is a plain mutableMapOf() and 26-28 is deleteSession { sessions.remove(uuid) }. A repo-wide grep for deleteSession returns only the interface declaration (SessionManager.kt:26) and the impl - zero call sites; grep for .cancel() across editor/ and ui/ returns only Fabric mixin ci.cancel() calls, so EditorSession.cancel() (EditorSessionImpl.kt:58) is also uncalled. getOrCreateSession callers confirmed at RecipeEditorCLI.kt:29,52, RecipesEditorUICommand.kt:40, EditorHomeStore.kt:21, AddIngredientsPage.kt:107, FormulaPage.kt:175, ResultPage.kt:90, SavingPage.kt:123. The leak is real, but severity ALTO is inflated: retention is one small EditorSessionImpl per distinct player UUID that opens the editor (bounded by unique editor users, not unbounded per action), and the thread-safety half of the claim is explicitly speculative ('if either ever moves off the main thread') with no evidence of an off-thread caller.

#### 🟡 EDITOR-7 — ResultModelImpl.complete() throws away the editor's own actions list and hardcodes alwaysKeepPrevious=false

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/item/ResultModelImpl.kt:30`

```
return Result.success(
    RecipeResult.of(
        choices = recipeChoices,
        modifier = itemModifier,
        actions = mutableListOf(),
        bulkActions = mutableListOf(),
        alwaysKeepPrevious = false
    )
)
```

**Impacto.** Model drift against core: core's `RecipeResult` (core/api/.../RecipeResult.kt:29-38) takes `actions`, `bulkActions` and `alwaysKeepPrevious`. The editor's `ResultModel` declares `val actions: List<ResultActionModel<*>>` (ResultModel.kt:9) and `RecipeResultUseCases.Choices.Add/Set/Remove` carefully carry `actions = result.actions.toMutableList()` through every edit (RecipeResultUseCases.kt:36, 57, 79) - but `complete()` then substitutes an empty list. So any result action a user configures is silently dropped on save and the persisted recipe runs no actions. Conversely `alwaysKeepPrevious` and `bulkActions` exist in core but have no field at all in `ResultModel`, so they can never be authored in the editor and are pinned to false/empty on every saved recipe.

**Arreglo.** Map the models: `actions = actions.map { it.complete().getOrElse { e -> return Result.failure(...) } }`, and add `bulkActions` and `alwaysKeepPrevious` fields to `ResultModel`/`ResultModelImpl` so the editor covers the whole core `RecipeResult` surface.

#### 🟡 EDITOR-8 — IngredientModel.replaceWithRemains is threaded through every use-case but is never read when building the core Ingredient

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/item/IngredientModelImpl.kt:50`

```
return@runBlocking Result.success(Ingredient.of(recipeChoices, completedMatcher, completedConsumer))
```

**Impacto.** Model drift against core: core's `Ingredient.of(choices, matching, consumption)` (core/api/.../ingredient/Ingredient.kt:16-24) has no `replaceWithRemains` parameter and the `Ingredient` interface exposes only `choices`, `matching`, `consumption` - remainder handling lives inside `IngredientConsumer.Consume(IngredientRemainder)`. The editor nevertheless declares `val replaceWithRemains: Boolean` (IngredientModel.kt:19), defaults it to `true` (IngredientModelImpl.kt:13), copies it through eight separate use-case rebuilds (IngredientUseCases.kt:71, 90, 110, 149, 170, 185, 221, 257) and surfaces it in the UI preview state (ui/common/.../state/UIIngredientPreview.kt:16). Concrete effect: a player toggling this field changes editor state that is discarded at save time - the saved recipe behaves identically either way. `loadFrom` (line 23) compounds the confusion by deriving it from an unrelated property: `(ingredient.consumption is IngredientConsumer.Consume)`.

**Arreglo.** Either delete `replaceWithRemains` from `IngredientModel`/`CustomIngredientModelImpl` and the UI preview, or make it drive the `IngredientRemainder` of the `Consume` consumer so it actually reaches `Ingredient.of`.

#### 🟡 EDITOR-9 — CLI reports "Saved recipe under X" unconditionally; a failed save is only written to the server log

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/cli/RecipeEditorCLI.kt:40`

```
if (model is SessionModel.CreateModel) {
    model.save(Key.customCrafting(recipeName))
} else if(model is SessionModel.EditModel) {
    model.saveAs(Key.customCrafting(recipeName))
}

ctx.source.sendSuccess({ Component.literal("Saved recipe under $recipeName") }, false)
```

**Impacto.** `SessionModel.save`/`saveAs` return Unit, and on a validation failure `SessionStateImpl.kt:24-27` / `49-52` only call `logger.error(...)` and return. Concrete path: player builds an incomplete recipe (no result choices -> ResultModelImpl.kt:17 "Result must have at least one stack or tag", or the far more common EDITOR-1 case) and runs `/recipes editor save my_recipe`. No file is written, but the player is told the recipe was saved, and then finds nothing after `/recipes reload`. The same handler also returns silently when the player has no active model (`val model = session.model ?: return@executes SUCCESS_RESULT`, line 30) - no message at all. Note also that the executor returns `0` (failure) after a successful save and `SUCCESS_RESULT` (1) on the early-return failure paths at lines 28 and 30, i.e. the brigadier result codes are inverted.

**Arreglo.** Change `SessionModel.save`/`saveAs` to return `Result<Unit>` (the whole model layer is already Result-based), then in the CLI send `sendFailure` with the exception message on failure and `sendSuccess` + `SUCCESS_RESULT` only on success.

#### ⚪ EDITOR-14 — runBlocking wrapper in CustomIngredientModelImpl.complete() with no suspending call inside

**Severidad:** BAJO · **Categoría:** `performance` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/domain/model/recipe/item/IngredientModelImpl.kt:36`

```
override fun complete(): Result<Ingredient> {
    return runBlocking {
        val recipeChoices = choices.complete().getOrElse {
```

**Impacto.** None of the three calls inside the block are `suspend` - `RecipeChoicesModel.complete`, `IngredientMatcherModel.complete` and `IngredientConsumerModel.complete` are all plain functions (RecipeChoicesModel.kt:13, IngredientMatcherModel.kt:12, IngredientConsumerModel.kt:7). `runBlocking` therefore only builds and parks an event loop on the calling thread for nothing. `complete()` runs on the main server thread (brigadier command executor -> `CreateRecipeSessionModel.save` -> `RecipeCraftingModelImpl.complete` -> formula -> ingredient), so it is a `BlockingEventLoop` allocation per ingredient per save, and it is a dangerous pattern to leave in place: the moment one of those `complete()` methods becomes `suspend`, this blocks the main thread on it.

**Arreglo.** Delete the `runBlocking { }` wrapper and the `return@runBlocking` labels; the body is already fully synchronous.

#### ⚪ EDITOR-15 — Editor command tree is rebuilt three times and takes a dispatcher argument it never uses

**Severidad:** BAJO · **Categoría:** `simplification` · **Ubicación:** `editor/common/src/main/kotlin/com/wolfyscript/customcrafting/editor/cli/commands/RecipesEditorCommand.kt:28`

```
sequenceOf(ROOT_NAME, "cc:$ROOT_NAME", "${Key.CUSTOMCRAFTING_NAMESPACE}:$ROOT_NAME").forEach { alias ->
    dispatcher.register(
        Commands.literal(alias).requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }.apply {
            recipeEditorCLIEntry(dispatcher)
        }
    )
}
```

**Impacto.** `recipeEditorCLIEntry(dispatcher)` (RecipeEditorCLI.kt:20) declares a `dispatcher: CommandDispatcher<CommandSourceStack>` parameter that is never referenced in its body - the whole tree is built through the `LiteralArgumentBuilder` receiver. Three independent copies of the same node graph are built and registered (and merged by brigadier into the tree `RecipesCommand` in core/common registers under the identical three aliases), which triples the command-node allocation and, more importantly, means the three aliases can silently drift apart if someone edits one path. The `Commands.redirect`/alias mechanism exists for this.

**Arreglo.** Build the tree once into a registered `LiteralCommandNode` and register the two alias literals as `Commands.literal(alias).redirect(node)`, and drop the unused `dispatcher` parameter from `recipeEditorCLIEntry`.


### GUI en juego — `ui/common`

<details><summary>Cobertura declarada por el lector (36 ficheros)</summary>

Read all 28 source files under ui/common/src (UIModule*, UIRegistries/Types, commands/RecipesEditorUICommand, editor/{Paths, RecipeEditorUI, EditorCustomUIProvider}, editor/home/*, editor/recipe_editor/{RecipeEditor, ResultEditor, IngredientEditor, IngredientMatcherMenu, RecipeChoiceMenus}, recipe_editor/crafting/* (RecipeCraftingEditor, CraftingPath, AddIngredientsPage, FormulaPage, ResultPage, SavingPage, ConditionsPage, ExtraPropertiesPage), recipe_editor/recipeitem/*, recipe_editor/state/UIIngredientPreview). To confirm call chains I also opened 8 files outside the slice: editor/common SessionManager(+Impl), EditorSession(+Impl), SessionModel, domain/model/recipe/ModelStateUtils.kt, RecipeCraftingModelImpl.kt, domain/usecase/{RecipeCraftingUseCases, IngredientUseCases}.kt and editor/common/cli/RecipeEditorCLI.kt (to check the brigadier literal merge of `recipes editor` between the two modules - that merge is correct, no finding). What I could NOT verify: the viewportl/scafall runtime itself is an external dependency with no sources in the repo or in the Gradle cache, so the semantics of `store()` lifetime, `Slot`/`ScrollSelect`/`Paged` internals, `ViewRuntime.openView()` threading, and whether `ItemStackSnapshot` implements value equality are unknown. I therefore did not report the `key(ingredient)` duplicate-key suspicion in AddIngredientsPage.kt:144, nor claim that `openView()` from the async task touches the server thread unsafely - only the part I could prove (the unsynchronised session map) is reported. Standard Compose semantics (remember/mutableStateOf/collectAsState/snapshot isolation) are treated as known and are the basis of UI-1.

</details>

#### 🟠 UI-1 — IngredientEditor submenu state is created without remember, so the four edit buttons never open anything

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/IngredientEditor.kt:144`

```
var currentSubMenu: SubMenu? by mutableStateOf(null)   // line 144, inside @Composable fun IngredientEditor(...)
...
166:        when (currentSubMenu) {
202:            Button(onClick = { currentSubMenu = SubMenu.STACK_CHOICES; store.fetchChoices(index) })
```

**Impacto.** `mutableStateOf` is called directly in the composable body with no `remember`, so a fresh MutableState is allocated on every composition. Line 166 reads it, so the click at line 202 invalidates and recomposes IngredientEditor, which re-runs line 144 and gets a brand-new state holding `null`; the write went to the discarded instance. Concrete: player runs /recipes editor -> crafting -> Ingredients -> places an item in an ingredient slot -> clicks 'Edit Ingredient' -> clicks 'Edit Choices' (or Tags/Matcher/Consumer). The `when` falls into the `else` branch every time, so the Stack-Choices / Tag-Choices / Matcher submenus can never be reached at all; only the side effect (store.fetchChoices/fetchTags/fetchMatcher) runs, pointlessly hitting the domain model on every click. Every other composable in the module uses `remember { mutableStateOf(...) }` (IngredientMatcherMenu.kt:82, RecipeChoiceMenus.kt:135, IngredientMatcherUI.kt:128,192), which confirms the intent.

**Arreglo.** var currentSubMenu: SubMenu? by remember { mutableStateOf(null) } (and keep it keyed by `index` if the submenu should reset when a different ingredient is opened: remember(index) { mutableStateOf<SubMenu?>(null) }).

> **Verificación adversarial — confirmado.** Confirmed at IngredientEditor.kt:144 — the line is literally `var currentSubMenu: SubMenu? by mutableStateOf(null)` with no `remember`, directly in the @Composable body of IngredientEditor (declared at :124-130). I looked for the usual escape hatches and found none: there is no `remember`/`rememberSaveable` wrapper, the value is not hoisted into the store (IngredientEditorStore at :34-115 holds only choices/tags/matcher StateFlows, no submenu field), and the composable is re-executed by its parent (AddIngredientsPage.kt:129-136 calls IngredientEditor inside a branch driven by `store.selectedIngredient`). The state is guaranteed to be discarded on any recomposition of IngredientEditor, and such a recomposition is triggered by the very click handlers in question: lines 145-147 read `store.choices/tags/matcher` via collectAsState in IngredientEditor's own restart scope, and each button at :202-224 calls store.fetchChoices/fetchTags/fetchMatcher, which update those MutableStateFlows (store lines 91-113) -> IngredientEditor recomposes -> line 144 re-allocates a state holding null -> `when (currentSubMenu)` at :166 falls to the `else` branch at :199. The finding's mechanism description (click alone re-runs line 144) depends on whether viewportl's `Column` content lambda is inline — I could not inspect the viewportl artifact (no sources in repo, nothing under ~/.gradle matching *viewportl*/wolfy) — but the outcome it claims (submenu state lost, submenus not reachable) holds either way. Corroborated by every sibling composable using the correct pattern: IngredientMatcherMenu.kt:82,164; RecipeChoiceMenus.kt:135; IngredientMatcherUI.kt:128,192.

#### 🟠 UI-2 — Formula slot assignment uses the position in a filtered icon list as the ingredient-collection index, assigning the wrong ingredient

**Severidad:** ALTO · **Categoría:** `correctness` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/crafting/FormulaPage.kt:149`

```
144:    fun getIngredientCollectionIcons(): List<ItemStackTemplate> {
145:        val stacks = mutableListOf<ItemStackTemplate>()
146:        stacks.add(FormulaPageDefaults.IngredientScrollSelectReset)
147:        getIngredientCollection.getCollection().ingredients.mapNotNullTo(stacks) {
148:            if (it is IngredientModel.CustomIngredientModel) {
149:                return@mapNotNullTo it.choices.stacks.firstOrNull()?.toTemplate()
150:            }
151:            null
152:        }
...
217:  ScrollSelect(onSubmit = { if (it >= 1) { store.setIngredientForSlot(index, it - 1) } ... })
```

**Impacto.** The bundle shown in each grid cell is built from a *filtered* list: ingredients with no stack choice yet (`firstOrNull()` == null) and every SavedIngredientModel are dropped, while `onSubmit` treats the picked bundle position minus one as the index into the *unfiltered* collection (RecipeCraftingUseCases.Formula.AssignIngredient -> formula.assignIngredient(index, collectionIndex), RecipeCraftingUseCases.kt:82-84). Concrete: on the Ingredients tab click 'Add Ingredient' twice, leave slot 0 empty and put STONE into the second ingredient. Go to Formula, scroll a grid cell to the only visible ingredient (bundle position 1) and submit -> setIngredientForSlot(slot, 0) -> the formula slot is bound to collection index 0, the *empty* ingredient, not the stone one. The recipe then either resolves to the wrong ingredient or fails to complete at save time ('Failed to resolve ingredient ... Missing ingredient in collection', RecipeCraftingModelImpl complete()).

**Arreglo.** Carry the real collection index through the icon list instead of the display position, e.g. build List<Pair<Int, ItemStackTemplate>> (or emit a placeholder template for unconfigured/saved ingredients so positions stay aligned) and map the submitted bundle position back to the stored collection index before calling setIngredientForSlot.

> **Verificación adversarial — confirmado.** Confirmed verbatim. FormulaPage.kt:144-154 builds the bundle as [IngredientScrollSelectReset] + `getCollection().ingredients.mapNotNullTo(...)` which drops every SavedIngredientModel (the `null` at :151) and every CustomIngredientModel whose `choices.stacks.firstOrNull()` is null (:149). FormulaPage.kt:217-222 then does `store.setIngredientForSlot(index, it - 1)`, and :129-132 -> RecipeCraftingUseCases.kt:82-84 `model.formula.assignIngredient(index, ingredientIndex)` -> RecipeCraftingModelImpl.kt:109-118 / :159-165 store `IngredientModelRefImpl(collectionIndex)`, which resolves with `collection.ingredients.getOrNull(indexInCollection)` (IngredientModelRefImpl.kt:9-11) — i.e. an index into the UNFILTERED list. No remapping table exists anywhere between the two. The prerequisite state is reachable from the GUI: AddIngredientsPage.kt:64-67 `addIngredient()` inserts a bare `CustomIngredientModelImpl()` with empty choices, and AddIngredientsPage.kt:82-84 `removeFirstStackChoiceFor` can empty an existing ingredient's choices while leaving it in the collection. Result is a silently wrong binding, or the save-time failure quoted from RecipeCraftingModelImpl.kt:130/:178 ('Missing ingredient in collection'). Nothing refutes it.

#### 🟠 UI-3 — Home swallows the 'already editing' failure and navigates anyway; nothing ever cancels or deletes a session, so a player can only ever create one recipe

**Severidad:** ALTO · **Categoría:** `bug` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/home/EditorHomeStore.kt:26`

```
21:        CustomCraftingProvider.get().server?.recipeEditor?.getOrCreateSession(viewer)?.fold(
22:            {
23:                it.create(recipeType)
24:            }
25:        ){
26:            // Do nothing for now
27:        }
// EditorHome.kt:35-38 -> Button(onClick = { editorStore.selectRecipeType(recipeType); backstack.add(Paths.RecipeEditor(recipeType)) })
```

**Impacto.** `EditorSessionImpl.create` returns Result.failure("Already editing a recipe of type ...") whenever `model != null` (EditorSessionImpl.kt:31-33), and the Result of `it.create(recipeType)` is discarded here while EditorHome.kt:37 pushes the editor page regardless. Nothing in the whole repo calls EditorSession.cancel() or SessionManager.deleteSession() (grepped: the only hits are the declaration and the implementation), and the GUI has no close/quit hook. Concrete: an admin opens /recipes editor, builds and saves a crafting recipe, presses 'Back' to Home and clicks the crafting icon again to start a second recipe. create() fails silently, no message is shown, and the editor reopens fully populated with the previous recipe's ingredients/formula/result. From then on every 'new' recipe is a copy of the first one for that player, for the rest of the server's uptime, and the EditorSession stays in SessionManagerImpl.sessions forever.

**Arreglo.** Handle the failure branch: report the error to the viewer and do not push Paths.RecipeEditor when create() fails; offer an explicit 'discard current recipe' action that calls EditorSession.cancel(). Also delete/cancel the session when the view is closed (and on player quit) so a fresh session is created next time.

> **Verificación adversarial — confirmado.** Every link of the chain re-read and confirmed. EditorHomeStore.kt:21-27: `getOrCreateSession(viewer)?.fold({ it.create(recipeType) }) { /* Do nothing for now */ }` — the trailing lambda is `onFailure` of the outer Result (which SessionManagerImpl.kt:14-24 always returns as success), and the Result returned by `it.create(...)` is the fold's return value, discarded by the expression statement. EditorSessionImpl.kt:30-33 does return `Result.failure(IllegalStateException("Already editing a recipe of type ..."))` when `model != null`, and `model` is only ever cleared by `cancel()` (EditorSessionImpl.kt:57-61). Grepping the whole repo for `cancel()`/`deleteSession` yields only the declarations and implementations (EditorSession.kt:34, SessionModel.kt:10, EditorSessionImpl.kt:58, SessionStateImpl.kt:38/57 — both empty bodies, SessionManager.kt:26, SessionManagerImpl.kt:26) plus unrelated Fabric mixin `ci.cancel()` calls; no production caller. Saving does not clear it either: SessionStateImpl.kt:47-55 `CreateRecipeSessionModel.save` only writes the file. And nothing resets on reopen: RecipesEditorUICommand.kt uses `getOrCreateSession` then just opens the view, and EditorHome.kt:35-38 pushes `Paths.RecipeEditor(recipeType)` unconditionally after `selectRecipeType`. So the stale-model + leaked-session behaviour is real; there is no closing/disposal hook (no DisposableEffect/onDispose anywhere in ui/common or editor/common).

#### 🟡 UI-4 — Tag selection page count is the number of tags, and it is read before the tag list is loaded; last page is always empty

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/RecipeChoiceMenus.kt:74`

```
72:    private var tags: List<HolderSet.Named<Item>> = emptyList()
74:    val totalPages: Int get() = tags.size
82:        if (fromIndex > toIndex || toIndex > tags.size) {
83:            return emptyList()
84:        }
...
114:                TagsState(
115:                    loading = false,
116:                    totalPages = CachedTags.totalPages,
117:                    tags = CachedTags.getTagsSubList(fromIndex, toIndex)
```

**Impacto.** Three defects in one place. (a) `totalPages` returns the tag count, not ceil(count/27), while the value is handed to `Paged(Modifier, tagPreviews.totalPages, ...)` (line 192) which expects a page count - with ~1300 vanilla item tags the selector advertises ~1300 pages of which only ~48 have content. (b) Kotlin evaluates named arguments in source order, so line 116 reads `CachedTags.totalPages` *before* line 117 triggers the lazy load; the very first time any player opens 'Add Item Tag' the list is still empty and the menu is built with totalPages = 0. (c) The bounds check at line 82 rejects instead of clamping, so the final partial page (fromIndex 1242, toIndex 1269 > size 1247) returns emptyList - the last ~20 tags are unreachable through the UI.

**Arreglo.** Make totalPages = ceil(tags.size / 27f).toInt(), populate the cache before reading totalPages (load first, then build the state), and clamp in getTagsSubList: tags.subList(fromIndex.coerceIn(0, tags.size), toIndex.coerceIn(fromIndex, tags.size)). The lazy init also races: `tags` is a non-volatile field assigned from storeCoroutineScope coroutines of different viewers - initialise it lazily with a thread-safe holder.

#### 🟡 UI-5 — Matcher-type 'next page' button is unbounded and the sublist start index is not clamped, throwing (and logging) on every click past the end

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/IngredientMatcherMenu.kt:56`

```
56:                    all.subList(fromIndex, toIndex.coerceAtMost(all.size))
57:                } catch (e: Exception) {
58:                    ScafallProvider.get().logger.error("Error while fetching custom ingredient-matcher models", e)
...
176:            Button(onClick = {
177:                page++
178:                onPageChange(page)
```

**Impacto.** Only `toIndex` is clamped; `fromIndex = page * 20` is not, and the next-page button at line 176 increments without any upper bound (SelectMatcherType gets no total-page count at all). Two matcher types are registered (UIRegistries.kt:46-51, exact + item). Concrete: open Ingredient -> Edit Matcher -> 'Select Other Type' -> click the next-page button once -> fromIndex=20, toIndex=coerceAtMost(2)=2 -> java.util.List.subList(20, 2) throws IllegalArgumentException, which is swallowed at line 57 and dumps a stack trace to the server console; the player just sees an empty selection panel with no way back to page 0 except the 'Cancel Selection' button. Every further click repeats the stack trace.

**Arreglo.** Compute a page count (ceil(all.size / 20f)) inside the store, expose it in AvailableMatchers, clamp fromIndex (`if (fromIndex >= all.size) return@update emptyList`), and disable/ignore the next-page button at the last page instead of relying on the catch-all.

#### 🟡 UI-6 — Formula grid rebuilds 9 bundle icons and re-reads the whole ingredient collection 9 times per recomposition

**Severidad:** MEDIO · **Categoría:** `performance` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/crafting/FormulaPage.kt:243`

```
223:  icon = formulaState.getIngredient(index).let { ingredientPreview ->
225:      ItemStack(Items.RED_BUNDLE, stack?.count ?: 1).apply {
232:          applyComponents(stack.components)
236:          update(DataComponents.LORE, ItemLore.EMPTY) { ItemLore(it.lines.toMutableList().apply { addAll(...) }) }
241:          set(
242:              DataComponents.BUNDLE_CONTENTS,
243:              BundleContents(store.getIngredientCollectionIcons())
244:          )
```

**Impacto.** This block sits directly in the composable body of the 3x3 loop, so every single recomposition of FormulaPageAdvanced (i.e. every click on the formula tab: toggling shaped/shapeless, trim, each symmetry button, each slot assignment) allocates 9 ItemStacks, 9 copies of the component map, 9 new lore lists, and calls getIngredientCollectionIcons() 9 times - each call walks the whole ingredient collection through withCraftingModel and allocates a new MutableList plus an ItemStackTemplate per ingredient. The identical bundle content is rebuilt nine times for the nine cells, and all of it is thrown away and re-sent to the client on the next click.

**Arreglo.** Hoist the icon list: `val collectionIcons = remember(formulaState) { store.getIngredientCollectionIcons() }` outside the repeat loops and build one BundleContents for all nine cells; wrap the per-cell ItemStack construction in `remember(ingredientPreview, collectionIcons)` so it is only rebuilt when the inputs actually change.

#### 🟡 UI-7 — /recipes editor creates the editor session and opens the view from an async thread, mutating an unsynchronised HashMap

**Severidad:** MEDIO · **Categoría:** `concurrency` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/commands/RecipesEditorUICommand.kt:39`

```
39:                ScafallProvider.get().scheduler.async(CustomCraftingProvider.get()) {
40:                    CustomCraftingProvider.get().server?.recipeEditor?.getOrCreateSession(executor.uuid)
41:                    viewportl.guiManager.getViewRuntime(executor.uuid).let { playerRuntime ->
42:                        playerRuntime.joinViewer(executor.uuid)
43:                        playerRuntime.setContent { RecipeEditorRoot() }
44:                        playerRuntime.openView()
```

**Impacto.** getOrCreateSession lands in SessionManagerImpl, whose backing store is a plain `mutableMapOf()` (SessionManagerImpl.kt:34: `private val sessions: MutableMap<UUID, EditorSession> = mutableMapOf()`) with no synchronisation. The brigadier command runs on the server thread but this body is dispatched to the scheduler's async pool, while the GUI pages call the same getOrCreateSession from composition (AddIngredientsPage.kt:107, FormulaPage.kt:175, ResultPage.kt:90, SavingPage.kt:123). Concrete: two operators run /recipes editor within the same tick, or one runs it while another player's editor page composes; two threads perform `sessions[uuid] = session` concurrently and a concurrent HashMap resize can drop an entry or corrupt a bucket chain - the losing player then hits 'Failed to fetch data from session: Session not available' (ModelStateUtils.kt:13) or silently edits a second, orphaned session.

**Arreglo.** Create the session on the calling (server) thread before dispatching, and/or back SessionManagerImpl with a ConcurrentHashMap and use computeIfAbsent in getOrCreateSession (today's `containsKey` + `get()!!` is itself a check-then-act race). Also confirm that joinViewer/setContent/openView are safe off the server thread; if viewportl does not re-dispatch internally, schedule that part synchronously.

#### 🟡 UI-9 — Shapeless formula: the 3x3 grid passes a slot index into a compact ingredient list, so the first cell can never be assigned

**Severidad:** MEDIO · **Categoría:** `correctness` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/crafting/FormulaPage.kt:219`

```
219:                                    store.setIngredientForSlot(index, it - 1)
// index = row * 3 + column (line 216), passed unchanged as the list position
// FormulaState.Shapeless.getIngredient(index) { if (ingredients.size > index) return ingredients[index] }  (lines 86-91)
```

**Impacto.** For Shapeless the model keeps a compact MutableList (ShapelessCraftingFormulaModel.ingredientRefs), not a 9-slot array, but the UI addresses it with the grid coordinate. Concrete: on the Formula tab toggle to Shapeless (empty ingredientRefs) and pick an ingredient in the top-left cell -> assignIngredient(0, x) with size 0 matches neither branch of the model's guard, so nothing is stored and nothing changes on screen - the editor looks broken. Pick in the second cell instead and the ingredient is appended at list position 0, i.e. it appears in the *first* cell. The displayed grid and the stored shapeless list therefore never agree.

**Arreglo.** Give the shapeless mode its own view over the compact list (e.g. render ingredientRefs.size cells plus one 'add' cell and address them by list position), or normalise in the store: translate the grid slot into an append/replace/remove on the compact list before calling assignIngredient/unassignIngredient.

#### ⚪ UI-8 — Stack-choices menu uses the clicked slot as the list index for replace/remove but ignores it for add

**Severidad:** BAJO · **Categoría:** `correctness` · **Ubicación:** `ui/common/src/main/kotlin/com/wolfyscript/customcrafting/ui/editor/recipe_editor/IngredientEditor.kt:171`

```
170:                    onRemove = { store.removeStackChoiceAt(index, it) },
171:                    onAdd = { _, stack -> store.addStackChoice(index, stack) },
172:                    onReplace = { choiceIndex, stack -> store.setStackChoiceAt(index, choiceIndex, stack) }
// RecipeChoiceMenus.kt:44-57 passes the slot index (row * 9 + col) as that first argument
```

**Impacto.** StackChoicesMenu hands the grid slot index to all three callbacks, but onAdd drops it and appends to the end of the choices list (IngredientUseCases.Choices.Add), while onReplace/onRemove use it as a list index. Concrete: an ingredient with one choice (slot 0 filled); the player drops IRON_INGOT into slot 4 -> it is appended at list position 1 -> on the next render the iron appears in slot 1 and slot 4 goes empty again, so the item visibly jumps. Note also that IngredientUseCases.Choices.Remove does `stacks.removeAt(index)` with no bounds check, so any future path that reports an empty change for a slot beyond the list size would throw inside the click handler.

**Arreglo.** Only render slots for `choices.size + 1` positions (or pass the slot index through to Add and have the use case pad/insert), so the slot index and the list index are the same thing; add a bounds guard in Choices.Remove/Set.


### Build, empaquetado, CI y recursos por defecto

<details><summary>Cobertura declarada por el lector (49 ficheros)</summary>

Read in full: build.gradle.kts, settings.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.properties, buildSrc/build.gradle.kts, buildSrc/settings.gradle.kts and all 5 convention plugins (build.docker.run, build.docs.changelog, build.settings.default, build.settings.fabric-loom, build.spigotlike) plus buildSrc/src/main/kotlin/utils/VersionUtils.kt; all 7 per-module build.gradle.kts (core/api, core/common, core/spigotlike, core/spigot, core/paper, core/fabric, editor/common, ui/common); all of .github (3 workflows, release_config.json, issue templates listed only); all of .changelog (4 settings json, 2 partials, 1 hbs); all shipped resources (core/fabric fabric.mod.json + customcrafting.mixins.json, core/common vals.properties, core/api resources.conf and the 17 default recipe .conf files - I read resources.conf and debug_stick.conf line by line and skimmed the rest for key shape). To cross-check descriptor/config keys against the real parser I also opened, outside my slice: ConfigurationManagerImpl.kt, SourceSettings.kt, ResourceSettings(.Impl).kt, DirectorySourceSettingsImpl.kt, SQLSourceSettingsImpl.kt, FilterSettingsImpl.kt, BackupSettings(.Impl + DirectoryBackupDestinationSettingsImpl).kt, DestinationFilter.kt, ResourceLoaderImpl.kt, SQLSource.kt, DataType.kt, RecipeManagerCommon.kt, ResourceUtils.kt, CustomCraftingProperties.kt, SentryUtils.kt, and grepped the whole tree for bstats/ProtocolLib/exposed/sentry/fabric-api usage and for the three entrypoint classes (all three exist, so no missing main class / entrypoint). NOT covered: I could not resolve the external `sharedLibs` version catalog (com.wolfyscript.scafall:scafall-versions:1.6.1) - it is not in ~/.gradle or ~/.m2 on this machine and there is no network - so I could NOT verify the concrete Minecraft version behind `apiVersion = sharedLibs.versions.minecraft.get()`, nor the exact contents of bundles.sentry / bundles.exposed / bundles.database.drivers / bundles.jackson, nor whether scafall provides kotlin-stdlib, kotlin-reflect and jackson at runtime (so I deliberately did not report the "kotlin-stdlib/jackson is neither shaded nor in plugin.yml libraries" suspicion). I also could not verify GitHub action tag existence (checkout@v7, action-gh-release@v3, deploy-pages@v5 etc.) without network, so I did not report them. I did not run any build.

</details>

#### 🟠 BUILD-1 — The release workflow publishes the root project's empty shadowJar to Modrinth, not the platform jars

**Severidad:** ALTO · **Categoría:** `config` · **Ubicación:** `build.gradle.kts:87`

```
line 45-47: `dependencies {\n    compileOnly(project(":core:core-spigot"))\n}`
line 87: `uploadFile.set(tasks.shadowJar) // Use the shadowed jar !!`
.github/workflows/publish_release.yml:24: `run: gradle build shadowJar artifactoryPublish modrinth`
```

**Impacto.** `tasks.shadowJar` inside the ROOT build.gradle.kts resolves to the root project's shadowJar. The root project has no source directory at all (`ls src` -> No such file or directory) and its only dependency is `compileOnly(project(":core:core-spigot"))`; `compileOnly` never appears on `runtimeClasspath`, which is shadow's default source configuration. The root shadowJar therefore contains nothing but META-INF/MANIFEST.MF. `modrinth` is only defined on the root project, so when a GitHub release is published, `gradle ... modrinth` uploads that ~1 KB empty jar to the `customcrafting` Modrinth project as a `release`. Every user who downloads the published artifact gets a jar with no classes; the real jars built by :core:spigot/:core:paper/:core:fabric shadowJar stay in build/libs and are never uploaded anywhere.

**Arreglo.** Move the `modrinth {}` block into core/paper (and/or core/spigot, core/fabric) where the real shadowJar lives, or in the root block set `uploadFile.set(project(":core:core-paper").tasks.named("shadowJar"))` and add `additionalFiles` for the other platforms. Also drop the pointless `compileOnly(project(":core:core-spigot"))` from the root project.

> **Verificación adversarial — confirmado.** Re-read D:/Github/eCustomCrafting/build.gradle.kts in full. Line 27 applies the shadow plugin to the ROOT project, lines 45-47 are exactly `dependencies { compileOnly(project(":core:core-spigot")) }`, and line 87 is exactly `uploadFile.set(tasks.shadowJar)` inside the root-scoped `modrinth { }` block (lines 82-98). `ls -la` of the repo root confirms there is no `src` directory (only .changelog, .github, buildSrc, core, docs, editor, gradle, ui), so the root main source set is empty; `compileOnly` is not extended by `runtimeClasspath`, which is shadow's default source configuration, so the root shadowJar carries no classes. A repo-wide grep for `modrinth` over every *.gradle.kts returns only build.gradle.kts:28/82/87 - no subproject defines a modrinth extension, so `gradle ... modrinth` in .github/workflows/publish_release.yml:24 (`run: gradle build shadowJar artifactoryPublish modrinth`) can only run the root task with the root's empty jar. Minotaur's `additionalFiles` is never set. Nothing refutes this; ALTO is appropriate for shipping an empty artifact as a public release.

#### 🟠 BUILD-2 — Exposed is used at runtime by core/api but is neither shaded into the jar nor declared in plugin.yml libraries

**Severidad:** ALTO · **Categoría:** `config` · **Ubicación:** `core/spigot/build.gradle.kts:87`

```
core/spigot/build.gradle.kts:86-92:
```
    libraries.apply {
//        libs.bundles.exposed.get().forEach {
//            add(it.toString())
//        }
        sharedLibs.bundles.database.drivers.get().forEach {
            add(it.toString())
        }
```
core/spigot/build.gradle.kts:54-62 shadowJar whitelist: only `include(project(coreApi|coreCommon|coreSpigotlike|coreSpigot))` and `sharedLibs.bundles.sentry`.
core/api/src/main/kotlin/.../resource/database/SQLSource.kt:12-19: `import org.jetbrains.exposed.v1.jdbc.Database` ... `import org.jetbrains.exposed.v1.jdbc.transactions.transaction`
```

**Impacto.** core/api declares `implementation(sharedLibs.bundles.exposed)` (core/api/build.gradle.kts:9) and really uses it (SQLSource.kt, DatabaseCache.kt, JsonValueTable.kt). In the spigot and paper shadowJars the shadow `dependencies { include(...) }` whitelist excludes everything except the four project modules and the sentry bundle, so no `org.jetbrains.exposed.*` class is packaged; and the plugin.yml `libraries` entry that would have made Paper/Spigot download it is commented out in both core/spigot (lines 87-89) and core/paper (lines 70-72), while the JDBC drivers ARE declared. Concrete path: an admin configures an `sql` source in plugins/<plugin>/config/resources/resources.conf (the file literally ships a commented template inviting this, resources.conf:26-33); ResourceLoaderImpl.kt:19 calls `it.configureFor(customCrafting, this)`, SQLSourceSettingsImpl.kt:23 does `return SQLSource(customCrafting, this)` and loading class SQLSource throws NoClassDefFoundError: org/jetbrains/exposed/v1/jdbc/Database during plugin enable. On Fabric it is worse: core/fabric/build.gradle.kts:29 declares the same `implementation(sharedLibs.bundles.exposed)`, the shadowJar at line 56 restricts itself to `configurations = listOf(shadow)` (which holds only coreApi/coreCommon), and Fabric has no `libraries` mechanism at all, so there is no way for Exposed to be present.

**Arreglo.** Uncomment and correct the libraries block to `sharedLibs.bundles.exposed.get().forEach { add(it.toString()) }` in both core/spigot and core/paper (note the commented code says `libs.bundles.exposed`, but gradle/libs.versions.toml has no [bundles] section - the correct accessor is `sharedLibs`), and for core/fabric add the exposed coordinates to the shadowJar include list (or move them into the `shadow` configuration).

> **Verificación adversarial — confirmado.** Verified each link. core/api/build.gradle.kts:9 is `implementation(sharedLibs.bundles.exposed)`; core/api/src/main/kotlin/.../resource/database/SQLSource.kt:11-19 really imports org.jetbrains.exposed.v1.core/jdbc and line 32 calls `Database.connect(...)`. core/spigot/build.gradle.kts:54-62 is a pure-include shadow filter (coreApi, coreCommon, coreSpigotlike, coreSpigot + sentry bundle) - with only include specs, shadow's DependencyFilter drops everything else, so no exposed classes are packaged; core/paper/build.gradle.kts:27-47 is the same, additionally restricted to `configurations = listOf(shadow)`. core/spigot/build.gradle.kts:86-92 and core/paper/build.gradle.kts:69-75 both have the `libs.bundles.exposed` loop commented out while the JDBC drivers bundle is live. core/fabric/build.gradle.kts:29 declares exposed as `implementation` but shadowJar at line 53-55 sets `configurations = listOf(project.configurations.shadow.get())` and shadow only holds coreApi/coreCommon (lines 26-27), with no jar-in-jar include. The build.spigotlike convention (buildSrc/src/main/kotlin/build.spigotlike.gradle.kts:41) declares exposed only as `compileOnly`, so nothing puts it on the runtime path either. I could not find any scafall artifact in the local Gradle cache to check whether the framework provides exposed transitively - that is the only unverified escape hatch, and nothing in this repo asserts it. On the repo evidence the finding stands.

#### 🟡 BUILD-3 — The SQL example commented into the shipped resources.conf uses keys the HOCON parser does not have; uncommenting it aborts plugin startup

**Severidad:** MEDIO · **Categoría:** `config` · **Ubicación:** `core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf:27`

```
resources.conf:26-33:
```
  // Load and save recipes from/to an SQL server
//  {
//    type = sql
//    host = ""
//    schema = ""
//    username = ""
//    password = ""
//  },
```
SQLSourceSettingsImpl.kt:8-13:
```
class SQLSourceSettingsImpl(
    override val connection: SourceSettings.SQLSourceSettings.DatabaseConnectionType,
    override val filter: SourceSettings.FilterSettings? = null,
    override val overwriteExisting: Boolean,
    override val propagateSavedResources: Boolean,
)
```
```

**Impacto.** The shipped default config documents an SQL source with top-level `host`/`schema`/`username`/`password`. The actual creator has no such properties: it needs a nested `connection { type = mysql, host = ..., database = ..., user = ..., password = ... }` (SourceSettings.kt:83-128) plus `overwriteExisting`. `connection` is a non-null, non-optional reference-typed creator parameter, so jackson-module-kotlin throws MismatchedInputException ("missing ... creator parameter connection which is a non-nullable type") - and additionally FAIL_ON_UNKNOWN_PROPERTIES would reject `host`/`schema`/`username`. This happens at ConfigurationManagerImpl.kt:85, `resourceSettings = configMapper.readValue<ResourceSettings>(resourcesSettingsFile)`, which is inside an `init {}` block, i.e. in the constructor. So an admin who follows the shipped instructions and uncomments the block gets an exception while constructing ConfigurationManagerImpl and the plugin never enables - with an error message that does not mention any of the keys they typed.

**Arreglo.** Rewrite the commented template in resources.conf to match SQLSourceSettingsImpl / DatabaseConnectionType, e.g. `{ type = sql, connection { type = mysql, host = "localhost:3306", database = "", user = "", password = "" }, overwriteExisting = true, propagateSavedResources = false }`, and add a regression test that deserializes the shipped default resources.conf with every commented block enabled.

> **Verificación adversarial — severidad corregida a MEDIO.** Re-read core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf: the commented SQL entry (lines 25-33) really is `type = sql` with flat `host`/`schema`/`username`/`password` and no `connection`, `overwriteExisting` or `propagateSavedResources`. SourceSettings.kt:55-57 declares `SQLSourceSettings.connection: DatabaseConnectionType` and SQLSourceSettingsImpl.kt:8-13 takes `connection`, `overwriteExisting` and `propagateSavedResources` as non-nullable creator params with no defaults (only `filter` has a default). ConfigurationManagerImpl.kt:79 registers the Kotlin module and line 85 (`resourceSettings = configMapper.readValue<ResourceSettings>(resourcesSettingsFile)`) is inside an `init {}` block, so the parse failure happens in the constructor. `sql` is a registered subtype (SourceSettings.kt:19) and is abstract-mapped to SQLSourceSettingsImpl (ConfigurationManagerImpl.kt:50-53), so it will genuinely reach that constructor and throw MismatchedInputException. The finding is factually correct, but I downgrade to MEDIO: the block ships commented out, the failure only triggers after a deliberate user edit, it is immediately visible at startup and trivially reverted, and per BUILD-2 the SQL path cannot work as packaged anyway - this is a stale doc template, not a defect in shipped behaviour.

#### 🟡 BUILD-5 — Paper and Spigot builds declare different plugin names, so the two jars use different data folders

**Severidad:** MEDIO · **Categoría:** `config` · **Ubicación:** `core/paper/build.gradle.kts:62`

```
core/paper/build.gradle.kts:61-64:
```
bukkitPluginYaml {
    name = "customcrafting"
    version = project.version.toString()
    main = "com.wolfyscript.customcrafting.paper.PaperLoaderPlugin"
```
core/spigot/build.gradle.kts:78-81:
```
bukkitPluginYaml {
    name = "CustomCrafting"
    version = project.version.toString()
    main = "com.wolfyscript.customcrafting.spigot.SpigotLoaderPlugin"
```
```

**Impacto.** Bukkit derives the data folder from the descriptor name (`new File(pluginsDir, description.getName())`). An admin who switches from the spigot jar to the paper jar (or back) on the same server suddenly gets `plugins/customcrafting/` instead of `plugins/CustomCrafting/`: the config/resources/recipes directory is recreated empty, ConfigurationManagerImpl.saveDefaults() re-exports the default resources.conf, and every recipe the admin created in-game appears to have been deleted (the old folder is still on disk, but the plugin never looks at it - and on a case-insensitive filesystem like Windows/macOS the two names collide instead, which is a different kind of surprise). It also breaks any third-party plugin that has `depend: [CustomCrafting]` in its own plugin.yml: on the Paper build that dependency is unresolvable and the dependent plugin refuses to load.

**Arreglo.** Use the same `name = "CustomCrafting"` in core/paper/build.gradle.kts as in core/spigot, or factor the descriptor name into a shared constant in buildSrc so the two cannot drift again.

#### 🟡 BUILD-6 — fabric.mod.json declares the CC0-1.0 license on a GPL-3.0 codebase and points `sources` at the scafall repository

**Severidad:** MEDIO · **Categoría:** `config` · **Ubicación:** `core/fabric/src/main/resources/fabric.mod.json:13`

```
core/fabric/src/main/resources/fabric.mod.json:10-13:
```
  "contact": {
    "sources": "https://github.com/WolfyScript/scafall"
  },
  "license": "CC0-1.0",
```
LICENSE:1-2: `GNU GENERAL PUBLIC LICENSE` / `Version 3, 29 June 2007`, and every build script and source file carries the GPLv3 header.
```

**Impacto.** The Fabric mod metadata is what mod launchers, Modrinth/CurseForge importers and license-scanning tooling read. It tells every consumer the mod is public-domain-equivalent (CC0-1.0) when the project is in fact GPL-3.0-or-later, which is the opposite of the intended copyleft: a redistributor relying on this metadata would legitimately believe they may relicense or close-source the mod. The `contact.sources` link is a copy-paste leftover from the scafall template and sends anyone who clicks "Source" in the mod menu to the wrong repository. Both are shipped verbatim inside every fabric jar, since processResources only expands `${...}` placeholders (core/fabric/build.gradle.kts:43-51) and never touches these two fields.

**Arreglo.** Set `"license": "GPL-3.0-or-later"` and `"contact": { "sources": "https://github.com/WolfyScript/CustomCrafting", "issues": ... }` in core/fabric/src/main/resources/fabric.mod.json; while there, fill in the empty `"description"` (line 6) and the lowercase `"name": "customcrafting"` (line 5).

#### 🟡 BUILD-7 — Fabric Loom mod dependencies declared with `implementation` instead of `modImplementation`, so they are never remapped

**Severidad:** MEDIO · **Categoría:** `api-misuse` · **Ubicación:** `core/fabric/build.gradle.kts:34`

```
core/fabric/build.gradle.kts:33-34:
```
    implementation(sharedLibs.fabric.loader)
    implementation(sharedLibs.fabric.api)
```
and the code really uses fabric-api - core/fabric/src/main/kotlin/com/wolfyscript/customcrafting/fabric/CustomCraftingFabricMod.kt:13-14:
```
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
```
```

**Impacto.** fabric-api is published against intermediary Minecraft names (e.g. `CommandRegistrationCallback.register` takes a `CommandDispatcher<class_2168>`). Loom only rewrites those signatures to the dev mapping set for dependencies added to a `mod*` configuration; a plain `implementation` puts the intermediary-named artifact straight on the compile classpath while the Minecraft jar on that same classpath (via build.settings.fabric-loom.gradle.kts:27 `minecraft(sharedLibs.minecraft)`) is in the named/dev mapping set. The two views of the same class do not unify, so `CommandRegistrationCallback.EVENT.register { dispatcher, ... }` in CustomCraftingFabricMod.kt cannot resolve against the named CommandSourceStack type. Any build of :core:core-fabric that actually touches a fabric-api entry point with a Minecraft type in its signature fails to compile, and if it does compile (pure-marker usages) the emitted references point at intermediary names that will not resolve in a named dev runtime.

**Arreglo.** Change lines 33-34 to `modImplementation(sharedLibs.fabric.loader)` and `modImplementation(sharedLibs.fabric.api)` (Loom's remapping configurations), keeping the `fabric.mod.json` `depends` entries as they are.

#### 🟡 BUILD-8 — create_release.yml truncates the version: `cut -d- -f2` drops everything after the first dash in the tag

**Severidad:** MEDIO · **Categoría:** `bug` · **Ubicación:** `.github/workflows/create_release.yml:22`

```
.github/workflows/create_release.yml:21-23:
```
        run: |
          version=$(echo ${{github.ref_name}} | cut -d- -f2)
          echo "::set-output name=version::$version"
```
and line 39 `name: ${{steps.get_version.outputs.version}}`; the workflow triggers on tags matching `v-**` (line 5) while gradle.properties:26 says `version = 5.0-alpha.4.0.0`.
```

**Impacto.** The project version itself contains dashes. For the tag that matches this project's own version scheme, `v-5.0-alpha.4.0.0`, `cut -d- -f2` splits on every dash and returns field 2, i.e. `5.0` (verified: `echo "v-5.0-alpha.4.0.0" | cut -d- -f2` -> `5.0`). The draft GitHub release is therefore named `5.0` for every single alpha, beta and rc tag, so consecutive pre-releases all get the same, wrong, title and are indistinguishable in the releases list. Only a plain `v-5.0.0`-style tag with no qualifier happens to work.

**Arreglo.** Strip only the prefix instead of splitting on dashes: `version=${GITHUB_REF_NAME#v-}`. While editing, replace the deprecated `::set-output` with `echo "version=$version" >> "$GITHUB_OUTPUT"`.

#### ⚪ BUILD-10 — The spigot and fabric docker test servers both bind host port 25569

**Severidad:** BAJO · **Categoría:** `config` · **Ubicación:** `core/fabric/build.gradle.kts:89`

```
core/fabric/build.gradle.kts:88-89:
```
            imageVersion.set("java${sharedLibs.versions.jdk.get()}")
            ports.add("25569:25565")
```
core/spigot/build.gradle.kts:110-111:
```
            imageVersion.set("java${sharedLibs.versions.jdk.get()}-graalvm")
            ports.add("25569:25565")
```
(core/paper/build.gradle.kts:93 correctly uses `ports.add("25570:25565")`.)
Also buildSrc/src/main/kotlin/build.docker.run.gradle.kts:8-9,41: `val debugPort: String = System.getenv("debugPort") ?: "5006"` / `ports.set(listOf(debugPortMapping))` - applied identically to all three modules.
```

**Impacto.** A developer who starts the spigot test server and then the fabric one (a normal thing to do when reproducing a cross-platform recipe bug) gets a podman/docker bind failure: `address already in use` on host port 25569, because the fabric container asks for the same host port the spigot container already holds. The `--replace` argument in build.docker.run.gradle.kts:39 does not help - it replaces a container of the same name, not one holding the port. The shared debug port 5006 has the same problem for the JDWP mapping, so the second server also cannot be attached to from the IDE.

**Arreglo.** Give the fabric server a distinct host port (e.g. `ports.add("25571:25565")`) and derive the debug port per-module in build.docker.run.gradle.kts (e.g. a base port plus an offset per platform) instead of hardcoding 5006 for all three.

#### ⚪ BUILD-11 — A plain `./gradlew build` writes jars into the developer's home directory because shadowJar is finalizedBy the docker copy task

**Severidad:** BAJO · **Categoría:** `config` · **Ubicación:** `core/spigot/build.gradle.kts:52`

```
core/spigot/build.gradle.kts:49-52:
```
    shadowJar {
        archiveFileName.set("${customArchiveName}.jar")

        finalizedBy("spigot_copy")
```
core/fabric/build.gradle.kts:58: `finalizedBy("fabric_copy")`
buildSrc/src/main/kotlin/build.docker.run.gradle.kts:11-13:
```
minecraftServers {
    serversDir.set(file("${System.getProperty("user.home")}/minecraft/test_servers_v5"))
}
```
```

**Impacto.** The shadow plugin wires `assemble` (and therefore `build`) to depend on `shadowJar`, so `finalizedBy("spigot_copy")`/`finalizedBy("fabric_copy")` fire on every ordinary build - including the CI job, which runs `gradle build shadowJar ...` (.github/workflows/publish_release.yml:24). Each such build copies the jar into `$HOME/minecraft/test_servers_v5/<server>/...`, i.e. writes outside the project tree on a first-time contributor's machine (and on the ephemeral CI runner) for a docker test server they never asked to run. It also couples jar assembly to a task whose failure - e.g. the server directory not existing - fails the build for a reason unrelated to compilation.

**Arreglo.** Drop the `finalizedBy("spigot_copy")` / `finalizedBy("fabric_copy")` lines and instead make the docker `*_run` tasks `dependsOn` the corresponding copy task, so the copy only happens when someone actually asks for a test server.

#### ⚪ BUILD-12 — Five unused imports in the changelog convention plugin

**Severidad:** BAJO · **Categoría:** `dead-code` · **Ubicación:** `buildSrc/src/main/kotlin/build.docs.changelog.gradle.kts:1`

```
buildSrc/src/main/kotlin/build.docs.changelog.gradle.kts:1-6:
```
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import se.bjurr.gitchangelog.api.model.Commit
import se.bjurr.gitchangelog.internal.semantic.ConventionalCommitParser
import se.bjurr.gitchangelog.plugin.gradle.HelperParam
import utils.getTagAt
```
Only `getTagAt` is referenced in the file body (lines 20 and 23); `Helper`, `Options`, `Commit`, `ConventionalCommitParser` and `HelperParam` appear nowhere else in the 27-line script.
```

**Impacto.** Leftovers from a removed custom-handlebars-helper implementation. `se.bjurr.gitchangelog.internal.semantic.ConventionalCommitParser` in particular is an `internal` package of the changelog plugin: the convention plugin will stop compiling - and with it every module that applies `build.docs.changelog` (core/spigot, core/paper, core/fabric), i.e. the whole release build - the day that plugin's `3.+` dynamic version (gradle/libs.versions.toml:16 `git-changelog = "3.+"`) picks up a build that moves or removes that internal class, for an import that is doing nothing.

**Arreglo.** Delete lines 1-5 of buildSrc/src/main/kotlin/build.docs.changelog.gradle.kts, keeping only `import utils.getTagAt`. Separately, consider pinning `git-changelog` to an exact version rather than the dynamic `3.+`, which makes the build non-reproducible.

#### ⚪ BUILD-9 — bStats and ProtocolLib are declared as `api` dependencies, relocated and added to plugin.yml libraries, but neither is used anywhere in the source

**Severidad:** BAJO · **Categoría:** `dead-code` · **Ubicación:** `buildSrc/src/main/kotlin/build.spigotlike.gradle.kts:44`

```
buildSrc/src/main/kotlin/build.spigotlike.gradle.kts:44-45:
```
    api(libs.protocollib)
    api(libs.bstats)
```
core/spigot/build.gradle.kts:65 `relocate("org.bstats", "com.wolfyscript.customcrafting.spigot.bstats")`, core/paper/build.gradle.kts:47 `relocate("org.bstats", "com.wolfyscript.customcrafting.bukkit.metrics")`, core/spigot/build.gradle.kts:97 and core/paper/build.gradle.kts:80 `libs.bstats.get().toString(),` inside `libraries.apply { ... }`.
`grep -rn "bstats|Metrics" --include=*.kt --include=*.java core` -> no matches. `grep -rn "com.comphenix" --include=*.kt --include=*.java core` -> no matches.
```

**Impacto.** Because `org.bstats:bstats-bukkit:3.0.0` is listed in the generated plugin.yml `libraries`, Paper's/Spigot's library loader resolves and downloads it from Maven Central on the very first startup of every single server that installs the plugin (and blocks enable while doing so), for a library the plugin never calls. The two `relocate("org.bstats", ...)` rules are equally dead - and are in fact mutually inconsistent with the `libraries` entry, since a library loaded by the server lands under `org.bstats` while the relocation would have rewritten call sites to `com.wolfyscript.customcrafting.*`, so if metrics are ever added the current setup will NoClassDefFoundError. ProtocolLib being `api` rather than `compileOnly` also leaks it into the published POMs of core-spigotlike/core-spigot/core-paper, so downstream consumers resolve ProtocolLib 5.3.0 they never asked for.

**Arreglo.** Delete `api(libs.protocollib)` and `api(libs.bstats)` from build.spigotlike.gradle.kts (or demote to `compileOnly`), remove the two `relocate("org.bstats", ...)` lines and the `libs.bstats.get().toString(),` entries from the `libraries` blocks in core/spigot and core/paper. Reinstate them together, consistently, when metrics are actually implemented.

---

## Apéndice A — hallazgos refutados

### ~~BUILD-4 — core/spigot publishes reobfJar, which paperweight builds from the thin `jar`, not from the shadowJar~~

Reportado como ALTO en `core/spigot/build.gradle.kts:75`. **Refutado.**

> The finding's load-bearing premise - "paperweight-userdev's reobfJar defaults its inputJar to tasks.jar.archiveFile" - is false for the version this project uses. buildSrc/src/main/kotlin/build.spigotlike.gradle.kts applies io.papermc.paperweight.userdev, resolved from the scafall catalog to 2.0.0-beta.x (paperweight-userdev 2.0.0-beta.14/17/18/21/23 are in the local cache). I decompiled ReobfArtifactConfiguration$Companion$MOJANG_PRODUCTION$1.configure with javap: bytecode offsets 30-92 do `tasks.named<AbstractArchiveTask>("shadowJar")` inside a try block whose exception handler (`Class org/gradle/api/UnknownTaskException`, offsets 54-92) falls back to `"jar"`, and configure$lambda$0 offsets 6-22 set `RemapJar.inputJar` to that provider's `archiveFile`. PaperweightUserExtension.<init> (offset 358-375) sets the convention of `reobfArtifactConfiguration` to `MOJANG_PRODUCTION`, and PaperweightUser.apply invokes it from an `afterEvaluate` block (offset 813), by which time core/spigot's shadow plugin (core/spigot/build.gradle.kts:29) has long since registered `shadowJar`. So reobfJar's input IS the fat shadowJar configured at core/spigot/build.gradle.kts:49-67, and `archives(tasks.reobfJar)` at line 75 publishes a complete, Spigot-remapped jar - the exact opposite of the claimed thin jar. The secondary claim is also undercut: PaperweightUser.decorateJarManifests (javap offsets 70 and 97 reference the string "shadowJar") decorates the shadowJar manifest, so core/paper's explicit `paperweight-mappings-namespace` attribute is not what distinguishes the two modules.

## Apéndice B — severidades corregidas por el verificador

| Id | Reportado | Corregido | Motivo |
|---|---|---|---|
| RECIPE-4 | ALTO | **MEDIO** | Code claim confirmed at RecipeIndex.kt:64-83: `updatedByKey` (:71) is only ever read via `remove` to drop stale entries from the plain `recipes` list; neither it nor the existing `byType` multimap is fed into byKeyBuilder/byTypeBuilder (:77… |
| RECIPE-6 | ALTO | **MEDIO** | Omission confirmed: `grep -rn areSatisfied` over the whole repo returns hits only in CustomRecipeCookingImpl.kt:28, CustomRecipeCraftingImpl.kt:22, CustomRecipeSmithingImpl.kt:30 and CustomRecipeStonecuttingImpl.kt:25. CustomRecipeRepairing… |
| CORE-1 | CRITICO | **ALTO** | Holds. MainCommand.kt:13-33 re-read verbatim: `dispatcher.register(Commands.literal(it).then(literal("info")...).then(literal("backup")...))` with NO `.requires {}` on the root or on any child node, while RecipesCommand.kt:26 does have `.re… |
| CORE-2 | ALTO | **MEDIO** | Holds factually. MainCommand.kt:26-28 is exactly `customCrafting.server!!.resourceManager.backupManager.createBackup()` followed by `ctx.source.sendSuccess({ Component.literal("Creating backup...") }, false)` — the call is a plain synchrono… |
| BUKKIT-6 | ALTO | **MEDIO** | SmithingListener.kt:91-102 is as quoted: endResult is computed, SmithingUtils.copyDataComponentsTo writes into it, and the function returns at 102 without ever assigning event.result, so the computed stack is discarded (real defect). But th… |
| FABRIC-4 | ALTO | **MEDIO** | The code claim is exact: AnvilMenuMixin.java:137 is an unguarded `(ServerPlayer) player` cast, while the same file guards at :80 (`player instanceof ServerPlayer serverPlayer`) and :142, and StonecutterResultSlotMixin.java:50 repeats the sa… |
| FABRIC-5 | ALTO | **MEDIO** | The mechanical facts check out: RecipeManagerCustomRecipeMixin.java:38-51 injects at RETURN of `prepare(ResourceManager, ProfilerFiller)RecipeMap`, which is the SimplePreparableReloadListener prepare phase run via CompletableFuture.supplyAs… |
| FABRIC-6 | ALTO | **MEDIO** | Verified all three pieces. EvaluationContextState.kt:3-14 is exactly `object` with a single plain `private var context: EvaluationContext?`, no ThreadLocal, no stack, and exit() unconditionally nulls it. CrafterMenuMixin.java:29-41 matches … |
| EDITOR-5 | ALTO | **MEDIO** | SessionManagerImpl.kt:7 is a plain mutableMapOf() and 26-28 is deleteSession { sessions.remove(uuid) }. A repo-wide grep for deleteSession returns only the interface declaration (SessionManager.kt:26) and the impl - zero call sites; grep fo… |
| BUILD-3 | ALTO | **MEDIO** | Re-read core/api/src/main/resources/com/wolfyscript/customcrafting/configuration/default/resources/resources.conf: the commented SQL entry (lines 25-33) really is `type = sql` with flat `host`/`schema`/`username`/`password` and no `connecti… |
