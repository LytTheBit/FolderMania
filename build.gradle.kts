import java.util.UUID

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.foldermania"
version = "0.2.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.swing")
}

dependencies {
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.github.weisj:jsvg:1.7.1") // SVG rendering
}

application {
    // Launcher does not extend Application, so it also works from the classpath (needed by jpackage)
    mainClass.set("com.foldermania.Launcher")
}

// ---------------------------------------------------------------------------
// Windows packaging with jpackage (bundles its own Java runtime: no JDK needed to run the app)
//
//   gradlew packageApp         -> build/jpackage/output/Folder Mania/Folder Mania.exe (portable folder)
//   gradlew packageInstaller   -> build/jpackage/output/Folder Mania-<version>.exe (installer)
//                                 requires WiX Toolset 3.x on the PATH (https://wixtoolset.org)
//   gradlew packageInstaller -PinstallerType=msi   -> same, but as an .msi
//
// Run these on Windows: the JavaFX jars that get bundled are the ones for the current OS.
// ---------------------------------------------------------------------------

val appDisplayName = "Folder Mania"
val packageDir = layout.buildDirectory.dir("jpackage")
val packageInput = layout.buildDirectory.dir("jpackage/input")
val packageOutput = layout.buildDirectory.dir("jpackage/output")

val prepareJpackageInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packageInput)
}

fun jpackageExecutable(): String {
    val home = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
    val exe = if (System.getProperty("os.name").startsWith("Windows")) "jpackage.exe" else "jpackage"
    return home.resolve("bin").resolve(exe).absolutePath
}

fun jpackageCommonArgs(type: String): List<String> = listOf(
    "--type", type,
    "--name", appDisplayName,
    "--app-version", project.version.toString(),
    "--vendor", "LytTheBit",
    "--description", "Customize Windows folders with colors and icons",
    "--input", packageInput.get().asFile.absolutePath,
    "--main-jar", tasks.jar.get().archiveFileName.get(),
    "--main-class", "com.foldermania.Launcher",
    "--icon", file("src/main/resources/app.ico").absolutePath,
    "--dest", packageOutput.get().asFile.absolutePath,
    // java.se = all standard modules, jdk.unsupported = needed by JavaFX. Can be slimmed down later.
    "--add-modules", "java.se,jdk.unsupported"
)

tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Builds a portable Windows app folder with its own Java runtime"
    dependsOn(prepareJpackageInput)
    doFirst {
        delete(packageOutput)
        executable = jpackageExecutable()
        args(jpackageCommonArgs("app-image"))
    }
}

tasks.register<Exec>("packageInstaller") {
    group = "distribution"
    description = "Builds a Windows installer (per-user, Start menu + desktop shortcut). Needs WiX 3.x"
    dependsOn(prepareJpackageInput)
    doFirst {
        delete(packageOutput)
        val type = (findProperty("installerType") as String?) ?: "exe"
        executable = jpackageExecutable()
        args(jpackageCommonArgs(type))
        args(
            "--win-per-user-install",   // no admin rights needed
            "--win-menu",               // Start menu entry
            "--win-shortcut",           // desktop shortcut
            "--win-dir-chooser",        // let the user pick the install folder
            // Stable id: lets a newer installer upgrade an older install instead of duplicating it
            "--win-upgrade-uuid", UUID.nameUUIDFromBytes("com.foldermania".toByteArray()).toString()
        )
    }
}
