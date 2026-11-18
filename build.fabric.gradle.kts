// build.fabric.gradle.kts — how to build the FABRIC jar for each Minecraft version.
// `sc` is Stonecutter: sc.current.version is the Minecraft version being built.

plugins {
    // Applies the correct Fabric Loom variant for this Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group = ... (Stonecutter manages it)
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "${sc.properties["mod.id"]}-fabric"

// Java version each Minecraft version requires.
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// In-game automated test (gradlew :1.21.8-fabric:runClientGameTest): opens a world, equips a cosmetic and
// saves screenshots. Lives in src/gametest and is never part of the released jar.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "htzcosmetics-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    /** Downloads only the Fabric API modules we actually use. */
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"]))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Official Mojang names on obfuscated versions (same names as 26.x -> easier porting).
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")
    // Full Fabric API at dev runtime, like players have installed (not bundled in our jar).
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"]}")

    // Only for the in-game test source set; never shipped.
    "modGametestImplementation"(fabricApi.module("fabric-client-gametest-api-v1", sc.properties["deps.fabric_api"]))

    // Some Fabric API modules were renamed over time.
    val keyModule = if (sc.current.parsed >= "26.1") "fabric-key-mapping-api-v1" else "fabric-key-binding-api-v1"
    val resourceModule = if (sc.current.parsed >= "1.21.11") "fabric-resource-loader-v1" else "fabric-resource-loader-v0"
    fapi("fabric-api-base", "fabric-command-api-v2", "fabric-lifecycle-events-v1", resourceModule,
        "fabric-rendering-v1", "fabric-screen-api-v1", keyModule)

    // Optional compatibility testing with third-party mods copied into devmods/mods (never committed):
    //   gradlew :1.21.8-fabric:runClientGameTest -PhtzCompat
    if (providers.gradleProperty("htzCompat").isPresent) {
        modLocalRuntime(fileTree(rootProject.file("devmods/mods")) { include("*.jar") })
        // Plain libraries those mods normally carry inside their jar (e.g. Iris' shader compiler).
        localRuntime(fileTree(rootProject.file("devmods/libs")) { include("*.jar") })
    }

    // Unit tests (JUnit). Only used when running tests, never shipped in the mod.
    testImplementation(platform("org.junit:junit-bom:${sc.properties["deps.junit"]}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
    }
    runConfigs.named("client") {
        // Your manual test folder (worlds, options), shared by all versions. The automated in-game test uses
        // its own throw-away folder (build/run/clientGameTest), because it deletes that folder before each run.
        runDirectory = rootProject.file("run")
        // Development only: load cosmetics from the locally built (and signed) site/ folder.
        // The in-game test (clientGameTest) does NOT set this, so it exercises the real GitHub Pages download.
        @Suppress("DEPRECATION") // Loom marks every run-config setter deprecated; no replacement yet
        property("htzcosmetics.devSite", rootProject.file("site").absolutePath)
    }
    // The in-game test inherits the client settings: blank the dev folder so it downloads from GitHub Pages.
    runConfigs.matching { it.name == "clientGameTest" }.configureEach {
        @Suppress("DEPRECATION")
        property("htzcosmetics.devSite", "")
        if (providers.gradleProperty("htzCompat").isPresent) {
            // Resource packs to enable during the compatibility test (e.g. Fresh Animations).
            @Suppress("DEPRECATION")
            property("htzcosmetics.test.packs", rootProject.file("devmods/resourcepacks").absolutePath)
            @Suppress("DEPRECATION")
            property("htzcosmetics.test.shaderpacks", rootProject.file("devmods/shaderpacks").absolutePath)
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    // Ship the license inside the mod jar.
    named<Jar>("jar") { from(rootProject.file("LICENSE")) { rename { "LICENSE_htzcosmetics" } } }

    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        // Fills the ${...} placeholders inside fabric.mod.json
        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", sc.properties["mod.version"])
            register("minecraft", sc.properties["mod.mc_compat"])
            // Minimums written into fabric.mod.json = exactly the versions we compile and test against.
            register("loader", sc.properties["deps.fabric_loader"])
            register("fabric_api", sc.properties["deps.fabric_api_min"])
        }
        filesMatching("fabric.mod.json") { expand(props) }

        // Files that belong to other loaders are not packed into the Fabric jar.
        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    }

    test {
        useJUnitPlatform()
        // Tests that scan our source code need to know where it is.
        systemProperty("htz.mainSrc", rootProject.file("src/main/java").absolutePath)
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies them to build/libs/{mod version}/"
        inputs.property("version", sc.properties["mod.version"])
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${sc.properties["mod.version"]}"))
    }
}
