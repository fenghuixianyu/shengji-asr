plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shengji.asr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shengji.asr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // 两个版本:
    //   std  = 只内置标准档 (SenseVoice 中文增强), 体积小
    //   full = 标准档 + 高精度档 (FunASR-Nano 大模型), 体积大
    flavorDimensions += "edition"
    productFlavors {
        create("std") {
            dimension = "edition"
            buildConfigField("boolean", "FULL_BUILD", "false")
            manifestPlaceholders["appLabel"] = "声迹"
        }
        create("full") {
            dimension = "edition"
            applicationIdSuffix = ".full"
            buildConfigField("boolean", "FULL_BUILD", "true")
            manifestPlaceholders["appLabel"] = "声迹 Pro"
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // 模型 .onnx 必须以未压缩形式打包, 否则 AAsset_getBuffer 会把它整份解压进内存
        noCompress.add("onnx")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
}