// Top-level build file. Stable AGP 8.x stack — production-tested
// combination used by the vast majority of Play Store apps today.
//
// Migration to AGP 9 + Kotlin 2.2 was attempted and rolled back because
// the Kotlin Gradle plugin's AGP 9 support is still maturing. Plan to
// upgrade in a v1.1.x release once that integration stabilises.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false
}
