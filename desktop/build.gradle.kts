import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// MOVA para Windows e Linux: Compose Multiplatform (Kotlin/JVM), com as regras
// de negócio compartilhadas com o Android pelo módulo :core.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

/** Versão exibida no app. */
val movaDesktopVersion = "0.2.1"

/** Número do build (no CI, o número da execução). */
val movaBuildNumber: Int = providers.environmentVariable("MOVA_BUILD_NUMBER").orNull?.toIntOrNull() ?: 0

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Informações do build disponíveis no código (versão e número do build).
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    val version = movaDesktopVersion
    val build = movaBuildNumber
    inputs.property("version", version)
    inputs.property("build", build)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("io/github/valeriofra/mova/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.github.valeriofra.mova.desktop
            |
            |/** Gerado pelo Gradle (desktop/build.gradle.kts). */
            |object BuildInfo {
            |    const val VERSION = "$version"
            |    const val BUILD = $build
            |}
            |""".trimMargin(),
        )
    }
}
sourceSets.main {
    kotlin.srcDir(generateBuildInfo)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material.icons.extended)
    implementation(libs.cmp.resources)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // Descompacta o FFmpeg do Linux (.tar.xz) sem depender do tar do sistema (ex.: dentro do Flatpak).
    implementation(libs.xz)
    // Janelas nativas do sistema para escolher pastas (Windows: IFileDialog; Linux: portal do Flatpak).
    implementation(libs.filekit.dialogs)
    // Barra de título integrada (JetBrains Runtime): some a barra branca do Windows.
    implementation(libs.jbr.api)
    // Pasta Downloads real do Windows (mesmo se o usuário a moveu para outro disco/OneDrive).
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.cmp.ui.test.junit4)
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        // No CI, mostra o resultado do teste com os componentes reais.
        showStandardStreams = providers.environmentVariable("MOVA_INTEGRATION").orNull == "1"
    }
}

compose.resources {
    packageOfResClass = "io.github.valeriofra.mova.desktop.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "io.github.valeriofra.mova.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx768m")
        // Só no app empacotado ($APPDIR existe): tela de abertura imediata e cache de
        // classes (AppCDS), criado na primeira execução, que acelera as seguintes.
        if (providers.gradleProperty("mova.release").orNull == "true") {
            jvmArgs += listOf(
                "-splash:\$APPDIR/resources/splash.png",
                "-XX:+AutoCreateSharedArchive",
                "-XX:SharedArchiveFile=\$APPDIR/mova.jsa",
            )
        }

        buildTypes.release.proguard {
            // O app é pequeno; o ganho do ProGuard não compensa o risco com reflexão.
            isEnabled.set(false)
        }

        nativeDistributions {
            // Linux: o pacote é o Flatpak (desktop/packaging/flatpak), montado a partir do app-image.
            targetFormats(TargetFormat.Msi)
            packageName = "MOVA"
            packageVersion = "${movaDesktopVersion.substringBeforeLast('.')}.$movaBuildNumber"
            description = "Baixe e organize vídeos e áudios com simplicidade"
            vendor = "MOVA"
            copyright = "© 2026 MOVA"
            // Arquivos copiados para o app (tela de abertura).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("packaging/app-resources"))
            // jdk.crypto.ec: HTTPS com servidores modernos; jdk.localedata: datas em pt/es/fr;
            // jdk.accessibility: leitores de tela no Windows.
            modules("java.instrument", "java.naming", "jdk.unsupported", "jdk.crypto.ec", "jdk.localedata", "jdk.accessibility")

            windows {
                iconFile.set(project.file("packaging/mova.ico"))
                // Fixo para sempre: é o que permite instalar versões novas por cima.
                upgradeUuid = "70a7e789-476f-49a2-9cb2-f6d8e7dfc035"
                // Instala só para o usuário (sem pedir administrador).
                perUserInstall = true
                dirChooser = false
                menu = true
                menuGroup = "MOVA"
                shortcut = true
            }
            linux {
                iconFile.set(project.file("packaging/mova.png"))
                packageName = "mova"
                debMaintainer = "mova@users.noreply.github.com"
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                shortcut = true
            }
        }
    }
}
