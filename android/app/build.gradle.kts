import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.paparazzi)
}

fun prop(nombre: String): String = providers.gradleProperty(nombre).get()

// Firma de release: se lee de variables de entorno (GitHub Actions) o de
// android/keystore.properties (tu ordenador, nunca se sube a git).
val firma = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secreto(clave: String, env: String): String? = System.getenv(env) ?: firma.getProperty(clave)

android {
    namespace = "com.agusmltkd.compas"
    compileSdk = 36

    defaultConfig {
        applicationId = prop("statio.applicationId")
        minSdk = 26
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"

        resValue("string", "app_name", prop("statio.nombreApp"))
        buildConfigField("String", "SUPABASE_URL", "\"${prop("statio.supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${prop("statio.supabaseKey")}\"")
        buildConfigField("String", "CLAVE_PUBLICA_LICENCIA", "\"${prop("statio.clavePublicaLicencia")}\"")
        buildConfigField("String", "URL_PRIVACIDAD", "\"${prop("statio.urlPrivacidad")}\"")
    }

    signingConfigs {
        create("release") {
            val ruta = secreto("storeFile", "KEYSTORE_PATH")
            if (ruta != null) {
                storeFile = file(ruta)
                storePassword = secreto("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secreto("keyAlias", "KEY_ALIAS")
                keyPassword = secreto("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", prop("statio.nombreApp") + " (pruebas)")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile != null) signingConfig = cfg
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
