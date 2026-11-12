import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    java
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.github.krpkbhdn"
version = "2.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

repositories {
    mavenCentral()
    google()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.jetbrains.pty4j:pty4j:0.14.0")
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.41.0-262.10968.63")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.41.0-262.10968.63")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/** Jewel's decorated window reaches into AWT internals; without these the custom title bar does not drag or resize. */
val awtOpens: List<String> = buildList {
    add("--add-opens=java.desktop/sun.awt=ALL-UNNAMED")
    add("--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED")
    val os = System.getProperty("os.name").lowercase()
    when {
        "win" in os -> add("--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED")
        "mac" in os -> { add("--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED"); add("--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED") }
        else -> add("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
    }
}

// Explicit override: claude does not count killed short dev runs as fullscreen start failures.
val claudeFullscreen = mapOf("CLAUDE_CODE_NO_FLICKER" to "1")

tasks.test {
    environment(claudeFullscreen)
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    // java.awt.event is opened only so the real-browser test can give synthetic key events the native rawCode CEF reads.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED", *awtOpens.toTypedArray())
}

val jbrHome: Provider<File> = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.asFile }

val verifyJcefRuntime = tasks.register("verifyJcefRuntime") {
    val release = jbrHome.map { File(it, "release") }
    inputs.files(release)
    doLast {
        val file = release.get()
        val text = if (file.isFile) file.readText() else ""
        val isJcefFlavour = Regex("""IMPLEMENTOR_VERSION="[^"]*-jcef"|MODULES="[^"]*jcef""").containsMatchIn(text)
        check(isJcefFlavour) {
            "The build needs JetBrains Runtime with JCEF (jbrsdk_jcef), but the toolchain at ${file.parentFile} is not that flavour " +
                "(its release file has neither an IMPLEMENTOR_VERSION ending in -jcef nor a jcef module). " +
                "Fix: the Gradle toolchain must resolve a JetBrains Runtime with JCEF (Foojay distribution jbrsdk_jcef, JBR 25); " +
                "if an older plain JBR was cached, delete ~/.gradle/jdks/jetbrains* and run the build again."
        }
    }
}
tasks.matching { it.name == "compileKotlin" || it.name == "compileTestKotlin" }.configureEach { dependsOn(verifyJcefRuntime) }

// A linked worktree has a .git file (a clone has a directory); its runs must not touch the main installation's data.
val isLinkedWorktree = layout.projectDirectory.file(".git").asFile.isFile
val worktreeDataDir = File(System.getProperty("java.io.tmpdir"), "cc-manager-worktrees/${projectDir.name}").absolutePath

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    if (isLinkedWorktree && System.getenv("CC_MANAGER_DATA_DIR") == null) {
        environment("CC_MANAGER_DATA_DIR", worktreeDataDir)
        logger.lifecycle("Worktree detected: data dir $worktreeDataDir")
    }
}

// Tests, previews and shots get their own home, data dir and Claude config dir under build/, so they never touch ~/.cc-manager or ~/.claude.
// Opt out with CCM_UNSANDBOXED, CCM_REAL_CLAUDE or CCM_SMOKE (those need the real claude login); runPreview --real keeps the real
// claude config but gets a fresh data dir under build/.
val sandboxHome = layout.buildDirectory.dir("sandbox-home").get().asFile.absolutePath
val sandboxOptOuts = listOf("CCM_UNSANDBOXED", "CCM_REAL_CLAUDE", "CCM_SMOKE")
val sandboxed = provider { sandboxOptOuts.none { providers.environmentVariable(it).isPresent } }
val sandboxedTasks = setOf("runPreview", "gitShots", "reviewShots", "webShots")

tasks.withType<Test>().configureEach {
    if (sandboxed.get()) {
        systemProperty("user.home", sandboxHome)
        environment("CC_MANAGER_DATA_DIR", "$sandboxHome/.cc-manager")
        environment("CLAUDE_CONFIG_DIR", "$sandboxHome/.claude")
    }
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "Boots the UI with fake services (flags: --light, --shots, --out=<dir>, --review sample visual review, --web real browser on the demo page)"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.PreviewMainKt"
    environment(claudeFullscreen)
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs("--enable-native-access=ALL-UNNAMED", *awtOpens.toTypedArray())
}

tasks.register<JavaExec>("gitShots") {
    group = "application"
    description = "Renders the Git tab offscreen to PNGs (flags: --out=<dir> --only=<names> --dark|--light --size=WxH)"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.git.GitShotsKt"
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs("--enable-native-access=ALL-UNNAMED", *awtOpens.toTypedArray())
}

tasks.register<JavaExec>("reviewShots") {
    group = "application"
    description = "Renders the Review screen offscreen to PNGs (flags: --out=<dir> --dark|--light --size=WxH)"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.review.ReviewShotsKt"
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs("--enable-native-access=ALL-UNNAMED", *awtOpens.toTypedArray())
}

tasks.register<JavaExec>("webShots") {
    group = "application"
    description = "Renders the Web review screen offscreen to PNGs (flags: --out=<dir> --dark|--light --size=WxH)"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.web.WebShotsKt"
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs("--enable-native-access=ALL-UNNAMED", *awtOpens.toTypedArray())
}

tasks.withType<JavaExec>().matching { it.name in sandboxedTasks }.configureEach {
    if (sandboxed.get()) {
        systemProperty("user.home", sandboxHome)
        environment("CC_MANAGER_DATA_DIR", "$sandboxHome/.cc-manager")
        environment("CLAUDE_CONFIG_DIR", "$sandboxHome/.claude")
    }
}

tasks.named<JavaExec>("runPreview") {
    doFirst {
        if (args.orEmpty().contains("--real")) {
            systemProperties.remove("user.home")
            val ownConfigDir = System.getenv("CLAUDE_CONFIG_DIR")
            if (ownConfigDir != null) environment("CLAUDE_CONFIG_DIR", ownConfigDir) else environment.remove("CLAUDE_CONFIG_DIR")
            environment("CC_MANAGER_DATA_DIR", layout.buildDirectory.dir("preview-real-data").get().asFile.absolutePath)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.github.krpkbhdn.ccmanager.ui.MainKt"
        javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Dcc-manager.update.version=$version") + awtOpens
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Claude Code Manager"
            packageVersion = version.toString()
            vendor = "krpkbhdn"
            // Spring reflects over many JDK modules; a trimmed jlink image breaks it at runtime.
            includeAllModules = true
            linux { iconFile.set(file("packaging/icon.png")) }
            macOS { iconFile.set(file("packaging/icon.icns")) }
            windows {
                iconFile.set(file("packaging/icon.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Claude Code Manager"
                upgradeUuid = "5b0f6a3e-7c1d-4e8a-9a52-3c1f0d2b7e41"
            }
        }
    }
}

tasks.register<JavaExec>("generateIcons") {
    group = "application"
    description = "Renders the app icon into packaging/icon.ico, icon.icns and icon.png used by the installers"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.GenerateIconsKt"
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    systemProperty("java.awt.headless", "true")
}
