plugins {
    alias(libs.plugins.android.application) apply false
    // 不再声明 `kotlin-android`：AGP 9 内置 Kotlin 支持（见 app/build.gradle.kts 说明）
    alias(libs.plugins.kotlin.compose) apply false
}
