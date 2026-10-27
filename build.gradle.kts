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
version = "1.2.25"

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
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.jetbrains.pty4j:pty4j:0.14.0")
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation("org.jetbrains.jediterm:jediterm-ui:3.76")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.41.0-262.10968.63")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.41.0-262.10968.63")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
    jvmArgs("--enable-native-access=ALL-UNNAMED", *awtOpens.toTypedArray())
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "Boots the UI with fake services (flags: --light, --shots, --out=<dir>)"
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
