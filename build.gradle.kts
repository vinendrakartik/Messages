plugins {
    alias(libs.plugins.android) apply false
    // Kotlin Android plugin is built-in to AGP 9.0.0, explicit alias removed to prevent conflicts
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
