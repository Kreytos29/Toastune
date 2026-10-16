import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":provider-youtube"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coroutines.swing)
    implementation(libs.okhttp)
    implementation(libs.json)
}

compose.desktop {
    application {
        mainClass = "app.toastune.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "toastune"
            packageVersion = "0.9.0"
            description = "Reproductor de música libre"
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
        }
    }
}
