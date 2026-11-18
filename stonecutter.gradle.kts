// stonecutter.gradle.kts — Stonecutter's control file.
// The line below says which version is "active" (the one your IDE shows and runs).
stonecutter active "1.21.8-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Lets stonecutter.properties.toml have sections per version and per loader.
    properties {
        tags(version, loader)
    }

    // Enables `//? if fabric {` ... `//?}` comments in Java code.
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    // `/*$ mod_version*/ "x"` in Java gets replaced by the real version string.
    swaps["mod_version"] = "\"${properties["mod.version"]}\";"
    swaps["minecraft"] = "\"${current.version}\";"

    replacements {
        // Mojang renamed ResourceLocation -> Identifier in 1.21.11.
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
