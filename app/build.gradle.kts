plugins {
    alias(libs.plugins.android.application)
    id("com.chaquo.python")
}

fun normalizeBaseUrl(rawValue: String): String {
    val trimmed = rawValue.trim().removeSuffix("/")
    val withoutEndpoint = if (trimmed.endsWith("/pricing/latest", ignoreCase = true)) {
        trimmed.removeSuffix("/pricing/latest")
    } else {
        trimmed
    }
    return "$withoutEndpoint/"
}

val pricingApiBaseUrl = normalizeBaseUrl(
    providers.gradleProperty("PRICING_API_BASE_URL")
        .orElse(providers.environmentVariable("PRICING_API_BASE_URL"))
        .orNull
        ?: "https://replace-me.onrender.com/"
)

android {
    namespace = "com.example.copra"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.copra"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PRICING_API_BASE_URL", "\"$pricingApiBaseUrl\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        mlModelBinding = true
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        buildPython("C:/Users/ADMIN/AppData/Local/Programs/Python/Python310/python.exe")
        pip {
            install("numpy")
            install("opencv-python-headless")
            install("scikit-image")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.camera.view)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation(libs.tensorflow.lite.gpu)
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    val camerax_version = "1.5.3"

    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
}
