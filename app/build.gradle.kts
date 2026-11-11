import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Assinatura fixa
//
// Para que cada nova versão instale POR CIMA da anterior, todo APK de release
// precisa ser assinado sempre com a mesma chave. A chave nunca fica no
// repositório: ela é lida de variáveis de ambiente (GitHub Actions) ou de um
// arquivo local `keystore.properties` (ignorado pelo git).
// ---------------------------------------------------------------------------
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("MOVA_KEYSTORE_FILE", "storeFile")
val hasReleaseSigning = releaseStoreFile != null

// versionCode precisa sempre aumentar para instalar por cima. No CI ele vem do
// número da execução do workflow; localmente usa 1.
val movaVersionCode = System.getenv("MOVA_VERSION_CODE")?.toIntOrNull() ?: 1
val movaVersionName = "0.2.0"

android {
    namespace = "io.github.valeriofra.mova"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.valeriofra.mova"
        minSdk = 29
        targetSdk = 36
        versionCode = movaVersionCode
        versionName = movaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // O mecanismo de extração (Python + FFmpeg) é nativo: incluímos só ARM 64 bits,
        // usado por praticamente todos os celulares atuais, para manter o APK menor.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("mova") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("MOVA_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("MOVA_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("MOVA_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sem a chave, o APK de release sai sem assinatura (não instala).
            // Isso evita gerar por engano um APK com assinatura diferente.
            signingConfig = signingConfigs.findByName("mova")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // O yt-dlp executa Python/FFmpeg a partir das bibliotecas nativas extraídas.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Capturas de tela (Roborazzi): grava os PNGs em app/build/outputs/roborazzi.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}
