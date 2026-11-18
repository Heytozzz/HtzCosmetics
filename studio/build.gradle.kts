// studio/build.gradle.kts — HtzCosmetics Studio, the owner's desktop app (Swing). Not part of the mod jar.
//
//   gradlew htzStudio          builds the app and opens it (the window stays open after Gradle finishes)
//   "HtzCosmetics Studio.bat"  same thing, by double-click
//
// It calls the tools/ classes directly for read-only checks and runs the usual Gradle/npm commands as child
// processes for everything that signs, publishes or deploys, so it never duplicates or weakens their rules.

plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val junitVersion = "6.1.3"
dependencies {
    implementation(project(":tools"))
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

application {
    mainClass = "com.heytozzz.htzcosmetics.studio.StudioApp"
}

val projectRoot: File = rootProject.projectDir

tasks.register("htzStudio") {
    group = "htzcosmetics"
    description = "Opens HtzCosmetics Studio (desktop app to manage, test and publish cosmetics)"
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/studio/lib")
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    doLast {
        val javaw = launcher.get().executablePath.asFile.resolveSibling("javaw.exe")
        val java = if (javaw.exists()) javaw else launcher.get().executablePath.asFile
        // Started detached so the app keeps running after this Gradle build ends.
        val command = mutableListOf<String>()
        if (System.getProperty("os.name").lowercase().contains("win")) command += listOf("cmd", "/c", "start", "\"\"")
        command += listOf(java.absolutePath, "-Dhtzstudio.root=${projectRoot.absolutePath}",
            "-cp", libDir.get().asFile.absolutePath + File.separator + "*", application.mainClass.get())
        ProcessBuilder(command).directory(projectRoot).start()
        println("HtzCosmetics Studio is opening...")
    }
}

// End-to-end check of the real window in dry-run mode (nothing is published), saving a screenshot of each screen:
//   gradlew :studio:studioSelfTest --args="<screenshot folder> [--with-client]"
tasks.register<JavaExec>("studioSelfTest") {
    group = "verification"
    description = "Drives HtzCosmetics Studio end to end (dry run) and saves screenshots"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.heytozzz.htzcosmetics.studio.StudioSelfTest"
    workingDir = projectRoot
}
