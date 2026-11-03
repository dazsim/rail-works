plugins {
    id("dev.kikugie.loom-back-compat")
}

val modVersion = providers.gradleProperty("version").get()
val mc = sc.current.version
val modName = sc.properties.get<String>("mod.name")
val author = sc.properties.get<String>("mod.author")
version = modVersion
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-fabric-$mc"

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = rootProject.file("src/main/resources/railworks.accesswidener")
    runConfigs.all {
        runDirectory = rootProject.file("runs/${sc.current.project}")
    }
}

sourceSets.main {
    java.exclude("com/workshopcraft/railworks/neoforge/**")
    resources.exclude("META-INF/neoforge.mods.toml", "railworks.neoforge.mixins.json")
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
            "fabric_minecraft_range" to sc.properties.get<String>("mod.mc_compat"),
            "fabric_loader_version" to sc.properties.get<String>("deps.fabric_loader"),
            "java_version" to "25",
        )
        inputs.properties(props)
        filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "*.mixins.json")) { expand(props) }
        rename("railworks\\.fabric\\.mixins\\.json", "railworks.mixins.json")
    }

    named<Jar>("sourcesJar") {
        rename("railworks\\.fabric\\.mixins\\.json", "railworks.mixins.json")
    }

    javadoc {
        title = "fabric $modVersion API"
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
            "Implementation-Title" to "fabric",
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to author,
            "Built-On-Minecraft" to mc,
        )
    }
}
