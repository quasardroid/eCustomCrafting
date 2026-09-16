import utils.archiveName

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    alias(sharedLibs.plugins.artifactory)
    alias(sharedLibs.plugins.shadow)
    alias(sharedLibs.plugins.resource.factory.bukkit)
    id("build.settings.default")
    id("build.spigotlike")
    id("build.docker.run")
    id("build.docs.changelog")
}

dependencies {
    implementation(shadow(projects.core.coreApi)!!)
    implementation(shadow(projects.core.coreCommon)!!)
    implementation(shadow(projects.core.coreSpigotlike)!!)

    paperweight.paperDevBundle(sharedLibs.versions.papermc.get())
}

val customArchiveName = archiveName("paper", sharedLibs.versions.minecraft.get())

tasks {
    shadowJar {
        // Mappings are in the runtime classpath. Not sure why they are included even though we use include for dependencies...
        // So to be sure nothing else slips in, just accept dependencies from the shadow configuration.
        configurations = listOf(project.configurations.shadow.get())

        archiveFileName.set("${customArchiveName}.jar")
        metaInf.duplicatesStrategy = DuplicatesStrategy.FAIL
        dependencies {
            include(project(project.projects.core.coreApi))
            include(project(project.projects.core.coreCommon))
            include(project(project.projects.core.coreSpigotlike))
            include(project(project.projects.core.corePaper))

            sharedLibs.bundles.sentry.get().forEach {
                include(dependency(it))
            }
        }
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        // bstats is not a dependency any more (it was never used); nothing to relocate.
        relocate("io.sentry", "com.wolfyscript.customcrafting.core.sentry")
//        relocate("com.fasterxml.jackson", "com.wolfyscript.scafall.lib.jackson")
    }
    gitChangelog {
        file.set(File(".changelog/core-paper.md"))
        settingsFile.set("${rootProject.rootDir.absolutePath}/.changelog/settings-core-paper.json")
    }
}

artifacts {
    archives(tasks.shadowJar)
}

bukkitPluginYaml {
    // Must match core/spigot: a differing plugin name gives the two jars DIFFERENT data folders,
    // so a server that switches from Spigot to Paper appears to lose all its recipes.
    name = "CustomCrafting"
    version = project.version.toString()
    main = "com.wolfyscript.customcrafting.paper.PaperLoaderPlugin"
    apiVersion = sharedLibs.versions.minecraft.get() // Only support the latest Minecraft version!
    authors.add("WolfyScript")
    depend.add("scafall")

    libraries.apply {
// Exposed is used at RUNTIME by core/api (the SQL resource source) but is not shaded into
        // this jar, so it must be declared here or the plugin hits NoClassDefFoundError.
        // NOTE: the accessor is `sharedLibs`; gradle/libs.versions.toml has no [bundles] section.
        sharedLibs.bundles.exposed.get().forEach {
            add(it.toString())
        }
        sharedLibs.bundles.database.drivers.get().forEach {
            add(it.toString())
        }

        addAll(
            sharedLibs.typesafe.config.get().toString(),
            sharedLibs.caffeine.get().toString(),
        )
    }
}

minecraftServers {
    libName.set("${customArchiveName}.jar")
    servers {
        register("paper") {
            destFileName.set("customcrafting.jar")
            version.set(sharedLibs.versions.minecraft.get())
            type.set("PAPER")
            imageVersion.set("java${sharedLibs.versions.jdk.get()}")
            ports.add("25570:25565")
        }
    }
}