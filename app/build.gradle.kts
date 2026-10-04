plugins {
    alias(libs.plugins.android.application)
    // 注意：AGP 9 起内置 Kotlin 支持，**不再需要** `org.jetbrains.kotlin.android`。
    // 若同时应用两者，会因重复注册 `kotlin` 扩展而失败：
    //   Cannot add extension with name 'kotlin', as there is an extension already
    //   registered with that name.
    // 参考：https://developer.android.com/build/migrate-to-built-in-kotlin
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.necoarc.ityou"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.necoarc.ityou"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "1.8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "ityou_release_keystore_pwd"
                keyAlias = System.getenv("KEY_ALIAS") ?: "ityou"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "ityou_release_keystore_pwd"
            } else {
                // 本地或 CI 未配置密钥时安全回退到 debug 签名
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug {
            // Compose 在 debug 构建下没有 R8 优化，滑动性能通常只有 release 的 1/2 ~ 1/3
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
        // 供设置页读取 versionName 构建信息，避免版本号在代码里硬编码后与 build.gradle 漂移
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// AGP 9 移除了 `android { kotlinOptions {} }`，改用 `kotlin.compilerOptions {}`。
// 官方要求 jvmTarget 与 android.compileOptions.targetCompatibility 保持一致。
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
