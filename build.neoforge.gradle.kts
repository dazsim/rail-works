plugins {
    id("net.neoforged.moddev")
    id("neoforge-mutex")
}

val modVersion = providers.gradleProperty("version").get()
val mc = sc.current.version
val modName = sc.properties.get<String>("mod.name")
val author = sc.properties.get<String>("mod.author")
version = modVersion
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-neoforge-$mc"

sourceSets.main {
    java.exclude("com/workshopcraft/railworks/fabric/**")
    resources.exclude("fabric.mod.json", "railworks.fabric.mixins.json")
}

neoForge {
    version = property("deps.neo_loader") as String
    accessTransformers.files.setFrom(rootProject.file("src/main/resources/META-INF/accesstransformer.cfg"))
    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod.id") as String)
        }
        register("client") {
            gameDirectory = rootProject.file("runs/${sc.current.project}")
            client()
        }
        register("server") {
            gameDirectory = rootProject.file("runs/${sc.current.project}-server")
            server()
        }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    processResources {
        val props = mapOf(
            "version" to modVersion,
            "mod_id" to sc.properties.get<String>("mod.id"),
            "mod_name" to modName,
            "mod_author" to author,
            "description" to sc.properties.get<String>("mod.description"),
            "license" to sc.properties.get<String>("mod.license"),
            "minecraft_version_range" to sc.properties.get<String>("mod.mc_compat"),
            "neoforge_version_range" to sc.properties.get<String>("deps.neo_range"),
            "neoforge_loader_version_range" to sc.properties.get<String>("deps.neo_loader_range"),
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta", "*.mixins.json")) { expand(props) }
        rename("railworks\\.neoforge\\.mixins\\.json", "railworks.mixins.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    named<Jar>("sourcesJar") {
        rename("railworks\\.neoforge\\.mixins\\.json", "railworks.mixins.json")
    }

    javadoc {
        title = "neoforge $modVersion API"
    }

    withType<Jar> {
        val name = project.property("mod.name")
        inputs.property("mod_name", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
    }

    jar {
        manifest.attributes(
            "Specification-Title" to modName,
            "Specification-Vendor" to author,
            "Specification-Version" to modVersion,
            "Implementation-Title" to "neoforge",
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to author,
            "Built-On-Minecraft" to mc,
        )
    }
}
