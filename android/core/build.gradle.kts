// Núcleo puro en Kotlin: horarios, recálculo, locuciones y licencia.
// No depende de Android, así que sus tests corren en cualquier JVM en segundos.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
