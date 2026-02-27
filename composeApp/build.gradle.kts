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
            implementation(compose.materialIconsExtended)
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

            // Main dispatcher for desktop (Swing event loop)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

sqldelight {
    databases {
        create("JoyufyDatabase") {
            packageName.set("com.aracem.joyufy.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aracem.joyufy.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Joyufy"
            packageVersion = "1.0.3"
            modules("java.sql", "java.naming")
            description = "Control personal de finanzas"
            copyright = "© 2026 Aracem"
            vendor = "Aracem"

            macOS {
                bundleID = "com.aracem.joyufy"
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                upgradeUuid = "a3f1e2d4-7b8c-4f5a-9e6d-1c2b3a4d5e6f"
                menuGroup = "Joyufy"
                perUserInstall = true
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
                packageName = "joyufy"
            }
        }
    }
}
