import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm("desktop")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Kotlinx
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)

            // SQLDelight Desktop driver
            implementation(libs.sqldelight.driver.sqlite)
            implementation(libs.sqlite.jdbc)
        }
    }
}

sqldelight {
    databases {
        create("NexlifyDatabase") {
            packageName.set("com.aracem.nexlify.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aracem.nexlify.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Nexlify"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }
    }
}
