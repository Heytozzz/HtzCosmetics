// tools/build.gradle.kts — offline tools for the mod OWNER. Not part of the mod jar.
//
//   gradlew htzKeygen                      creates your signing keys (once)
//   gradlew htzKeygen --args="--rotate"    replaces a leaked primary key with the backup
//   gradlew htzBuildSite                   converts cosmetics/ into a signed site/ folder
//   gradlew htzVerifySite                  re-checks site/ exactly like the mod would

plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java {
            // Reuse the mod's pure-Java core so the tools validate with EXACTLY the same rules as the mod.
            srcDir(rootProject.file("src/main/java"))
            include("com/heytozzz/htzcosmetics/core/**", "com/heytozzz/htzcosmetics/tools/**")
        }
    }
}

val junitVersion = "6.1.3"
dependencies {
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val projectRoot: File = rootProject.projectDir

fun registerTool(name: String, mainClassName: String, text: String) {
    tasks.register<JavaExec>(name) {
        group = "htzcosmetics"
        description = text
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "com.heytozzz.htzcosmetics.tools.$mainClassName"
        workingDir = projectRoot
    }
}

registerTool("htzKeygen", "KeyGenCommand", "Creates (or rotates) the Ed25519 manifest signing keys")
registerTool("htzBuildSite", "BuildSiteCommand", "Builds and signs site/ from the cosmetics/ folder")
registerTool("htzVerifySite", "VerifySiteCommand", "Verifies site/ with the same checks the mod uses")
