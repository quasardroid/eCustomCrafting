package com.wolfyscript.customcrafting.editor

import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class SessionManagerImpl : SessionManager {

    private companion object {
        /**
         * How long an untouched session is kept.
         *
         * `deleteSession` had no caller anywhere in the repo, so a session was created on the
         * player's first `/recipes editor` and then lived until the server stopped — one per player
         * who ever opened the editor, each holding a whole recipe model.
         *
         * The editor module is platform-agnostic and has no disconnect hook of its own, so idle
         * expiry is the bound that does not require a Bukkit/Fabric listener. Wire a real quit
         * handler to [deleteSession] when the module gains a platform lifecycle hook.
         */
        private val SESSION_TTL_NANOS = Duration.ofHours(1).toNanos()
    }

    private class TrackedSession(val session: EditorSession) {
        @Volatile
        var lastAccessNanos: Long = System.nanoTime()
    }

    // Sessions are created from command handlers that dispatch to an async scheduler, so this map is
    // genuinely touched from more than one thread.
    private val sessions: MutableMap<UUID, TrackedSession> = ConcurrentHashMap()

    private fun purgeExpired() {
        val now = System.nanoTime()
        sessions.entries.removeIf { now - it.value.lastAccessNanos > SESSION_TTL_NANOS }
    }

    private fun touch(tracked: TrackedSession): EditorSession {
        tracked.lastAccessNanos = System.nanoTime()
        return tracked.session
    }

    override fun getSession(uuid: UUID): EditorSession? {
        purgeExpired()
        return sessions[uuid]?.let { touch(it) }
    }

    override fun createSession(uuid: UUID): Result<EditorSession> {
        purgeExpired()
        val tracked = TrackedSession(EditorSessionImpl(uuid))
        sessions[uuid] = tracked
        return Result.success(tracked.session)
    }

    override fun getOrCreateSession(uuid: UUID): Result<EditorSession> {
        purgeExpired()
        // Atomic: `containsKey` followed by `get()!!` could NPE when another thread removed the
        // session in between.
        return Result.success(touch(sessions.computeIfAbsent(uuid) { TrackedSession(EditorSessionImpl(it)) }))
    }

    override fun deleteSession(uuid: UUID) {
        sessions.remove(uuid)
    }

}
