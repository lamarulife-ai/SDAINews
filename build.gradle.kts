// Top-level build file. Stable AGP 8.x stack — production-tested
// combination used by the vast majority of Play Store apps today.
//
// Migration to AGP 9 + Kotlin 2.2 was attempted and rolled back because
// the Kotlin Gradle plugin's AGP 9 support is still maturing. Plan to
// upgrade in a v1.1.x release once that integration stabilises.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
