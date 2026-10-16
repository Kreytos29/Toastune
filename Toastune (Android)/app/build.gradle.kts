plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.toastune"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.toastune"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.7.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor usa APIs de java.* que Android < 13 no trae
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firma de depuración para poder instalar la versión release sin crear un keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "/META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":provider-youtube"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.coil.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.haze)
    implementation(libs.palette)
    implementation(libs.profileinstaller)
    implementation(libs.play.auth)

    coreLibraryDesugaring(libs.desugar.nio)
}
