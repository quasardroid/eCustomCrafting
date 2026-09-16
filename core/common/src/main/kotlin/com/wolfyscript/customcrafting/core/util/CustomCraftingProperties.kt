package com.wolfyscript.customcrafting.core.util

import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Object to handle the loading and accessing of properties from the vals.properties file.
 */
object CustomCraftingProperties {

    /**
     * Properties object to store the properties loaded from the vals.properties file.
     */
    val properties: Properties = Properties()

    init {
        // Close the stream, and survive a missing resource: this runs in a static initializer, so an
        // NPE here becomes an ExceptionInInitializerError that takes the whole plugin down. The
        // defaults below already cover every value.
        val resource = javaClass.classLoader
            .getResourceAsStream("com/wolfyscript/customcrafting/vals.properties")
        if (resource == null) {
            LoggerFactory.getLogger(CustomCraftingProperties::class.java)
                .warn("vals.properties is missing from the jar; falling back to defaults")
        } else {
            resource.use { properties.load(it) }
        }
    }

    /**
     * The release version of the application.
     *
     * @return The release version as a string, or "unknown" if not found in the properties.
     */
    val release: String = properties.getProperty("release") ?: "unknown"

    /**
     * The Sentry DSN (Data Source Name) for error reporting.
     *
     * @return The Sentry DSN as a string, or an empty string if not found in the properties.
     */
    val sentryDsn: String = properties.getProperty("sentry.dsn") ?: ""

    /**
     * Whether Sentry error reporting is enabled.
     *
     * @return True if Sentry is enabled, false otherwise.
     */
    val sentryEnabled: Boolean = properties.getProperty("sentry.enabled")?.toBoolean() ?: false

}