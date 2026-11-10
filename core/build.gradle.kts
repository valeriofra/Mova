import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo de regras de negócio em Kotlin puro (sem APIs Android).
// Mantido independente para poder ser compartilhado com a futura versão Windows.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
