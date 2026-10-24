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
version = "1.0.6"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "Boots the UI with fake services (flags: --light, --shots, --out=<dir>)"
    mainClass = "com.github.krpkbhdn.ccmanager.ui.PreviewMainKt"
    classpath = sourceSets["test"].runtimeClasspath
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.github.krpkbhdn.ccmanager.ui.MainKt"
        javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Claude Code Manager"
            packageVersion = version.toString()
        }
    }
}
