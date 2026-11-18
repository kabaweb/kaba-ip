plugins {
    kotlin("jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.material3)
    implementation(compose.components.resources)
}

compose.desktop {
    application {
        mainClass = "com.ipvio.stream.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "KorpTV"
            packageVersion = "1.0.0"
            description = "Korp TV Desktop Player"
            copyright = "© 2026 Korp TV"
            vendor = "Kabaweb"
        }
    }
}
