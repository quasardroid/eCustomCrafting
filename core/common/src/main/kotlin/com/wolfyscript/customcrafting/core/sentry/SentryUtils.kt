package com.wolfyscript.customcrafting.core.sentry

import com.wolfyscript.customcrafting.core.util.CustomCraftingProperties
import com.wolfyscript.scafall.platform.PlatformType
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.log4j2.SentryAppender
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LoggerContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

fun setupSentry(
    minecraftVersion: String,
    platformType: PlatformType,
    dataDir: File,
    additionalScopeConfig: ScopeCallback? = null
) {
    initSentry(dataDir)

    Sentry.configureScope { scope ->
        scope.setTag("minecraft.version", minecraftVersion)
        scope.setTag("platform.type", platformType.name)
        additionalScopeConfig?.run(scope)
    }
}

/**
 * Initiates the Sentry SDK and inserts the [SentryAppender] into the Log4J logger.
 *
 * The Log4J Appender is necessary to catch errors that are not caught with try-catch or other means.
 * This way we can catch errors very early on in the plugin/mod lifecycle.
 */
private const val SENTRY_APPENDER_NAME = "customcrafting:sentry"

/**
 * Detaches the Log4J appender from the global root logger and shuts Sentry down.
 *
 * The appender is attached to the JVM-wide root logger, which outlives the plugin: without this a
 * `/reload` (or a disable) left the old appender and an open Sentry client behind, one more of each
 * on every cycle.
 */
fun teardownSentry() {
    val logCtx = LoggerContext.getContext(false)
    val logConfig = logCtx.configuration
    val existing = logConfig.appenders[SENTRY_APPENDER_NAME]
    if (existing != null) {
        logConfig.rootLogger.removeAppender(SENTRY_APPENDER_NAME)
        logCtx.updateLoggers()
        existing.stop()
    }
    Sentry.close()
}

private fun initSentry(dataDir: File) {
    // Idempotent: re-attaching on a second init would double every reported event.
    if (LoggerContext.getContext(false).configuration.appenders.containsKey(SENTRY_APPENDER_NAME)) {
        return
    }
    val sentryAppender = SentryAppender.createAppender(
        SENTRY_APPENDER_NAME,
        null,
        null,
        null,
        "", // Do not init sentry in the appender, we initiate it manually below
        false,
        null,
        null
    )
    if (sentryAppender != null) {
        // have to add the Log4J Appender to the existing logger to watch for uncaught exceptions.
        // it's a bit tricky (and officially unsupported), but it's the only way and *shouldn't* conflict with anything
        sentryAppender.start()
        val logCtx = LoggerContext.getContext(false)
        val logConfig = logCtx.configuration
        logConfig.addAppender(sentryAppender)
        logConfig.rootLogger.addAppender(sentryAppender, Level.ERROR, null)
        logCtx.updateLoggers()
    }

    /**
     * By default, sentry transmits the "server name", that seems to be the name of the machine.
     * (I assume that's because it is meant for servers the program owner manages, i.e., web servers)
     * We don't want that data because the servers are managed by users and the names may contain personal data, instead generate a unique id.
     * The unique id is useful to determine if errors are widespread or only affect specific configurations.
     */
    var id: UUID? = null

    val idFile = File(dataDir, "sentry_id")
    if (!idFile.exists()) {
        idFile.parentFile.mkdirs()
        try {
            FileOutputStream(idFile).use {
                it.write(UUID.randomUUID().toString().toByteArray())
            }
        } catch (e: Exception) {
            // Ignore exceptions
        }
    }

    if (idFile.exists()) {
        // The write above swallows its exceptions, so a failed/partial write leaves a zero-byte
        // file; UUID.fromString("") then threw out of the plugin constructor and stopped
        // CustomCrafting from loading at all. Telemetry setup must never be able to do that.
        id = try {
            FileInputStream(idFile).use {
                UUID.fromString(String(it.readAllBytes()).trim())
            }
        } catch (e: Exception) {
            idFile.delete()
            val regenerated = UUID.randomUUID()
            try {
                FileOutputStream(idFile).use { out ->
                    out.write(regenerated.toString().toByteArray())
                }
            } catch (_: Exception) {
                // Ignore exceptions; an in-memory id is good enough for this run.
            }
            regenerated
        }
    }

    // Server-side opt-out. `CustomCraftingProperties.sentryEnabled` is baked into the jar at build
    // time, so without this a server owner could not turn outbound error reporting off at all
    // without repacking the jar. This is checked here rather than in the plugin config because
    // setupSentry runs from the plugin/mod constructor, long before any config is parsed.
    val telemetryOptOut = File(dataDir, "telemetry.disabled").exists()
    val telemetryEnabled = CustomCraftingProperties.sentryEnabled && !telemetryOptOut

    Sentry.init {
        it.isEnabled = telemetryEnabled
        it.dsn = CustomCraftingProperties.sentryDsn
        it.release = CustomCraftingProperties.release
        it.serverName = id?.toString() ?: UUID.randomUUID().toString()
        it.isSendDefaultPii = false // don't send personal identifiable information (ip, computer name, etc.)
        it.tracesSampleRate = null // make sure tracing is always disabled. we don't care about performance monitoring.
        it.addInAppInclude("com.wolfyscript") // we are capturing errors related to any project of mine, just to make sure none are lost.
        it.isEnableUncaughtExceptionHandler = true

        // just some debug stuff, should be disabled in production!
//        it.isDebug = false
//        it.setLogger(SystemOutLogger())

        it.beforeSend = SentryOptions.BeforeSendCallback({ event, hint ->
            // In case the error originates in an external logger we need to filter out unrelated errors
            // e.i. errors from other mods/plugins, vanilla minecraft errors, etc.
            val inAppLogger = event.logger?.startsWith("com.wolfyscript") ?: false
            if (!inAppLogger) {
                // Filter out errors that do not contain the com.wolfyscript package
                // or the ones without any exceptions
                val inApp = event.exceptions?.any { exception ->
                    exception.stacktrace?.frames?.any { frame ->
                        frame.isInApp ?: false
                    } ?: false
                } ?: false

                if (!inApp) {
                    return@BeforeSendCallback null
                }
            }

            return@BeforeSendCallback event
        })
    }
}