// Версии плагинов заданы в settings.gradle.kts → pluginManagement.plugins.
// Корневой файл их только регистрирует; фактически применяются они в :app.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("com.google.devtools.ksp") apply false
}
