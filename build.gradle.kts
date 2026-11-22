plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.foldermania"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls", "javafx.swing")
}

dependencies {
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

application {
    mainClass.set("com.foldermania.ui.MainApp")
}
