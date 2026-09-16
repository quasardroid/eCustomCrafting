package com.wolfyscript.customcrafting.core.recipe.evaluation

object EvaluationContextState {

    /**
     * Contexts are entered from block-entity ticks and from menu code, which do not all run on the
     * same thread, and they nest. A single shared field let one thread's context become another
     * thread's "current", and any unbalanced [exit] wiped a context that was still in use.
     *
     * A per-thread stack keeps the state thread-confined, makes nesting work, and turns a stray
     * [exit] into a no-op instead of a silent corruption.
     */
    private val contexts = ThreadLocal.withInitial { ArrayDeque<EvaluationContext>() }

    val current: EvaluationContext? get() = contexts.get().lastOrNull()

    fun enter(context: EvaluationContext) {
        contexts.get().addLast(context)
    }

    fun exit() {
        contexts.get().removeLastOrNull()
    }

    fun run(context: EvaluationContext, block: EvaluationContext.() -> Unit) {
        enter(context)
        try {
            block(context)
        } finally {
            exit()
        }
    }

}