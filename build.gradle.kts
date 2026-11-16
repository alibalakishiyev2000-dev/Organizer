// Корневой файл сборки проекта «Органайзер» (AGP 9.1.1 со встроенным Kotlin)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
