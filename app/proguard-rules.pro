# SD AI News — release-build keep rules.

# Required attributes for Moshi reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes Exceptions

# Kotlin reflection metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Moshi reflective adapter — our wire models are parsed by reflection
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }
-keepnames @com.squareup.moshi.JsonClass class *

# Our data classes — referenced by Moshi via reflection
-keep class com.sdai.news.data.** { *; }
-keepclassmembers class com.sdai.news.data.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# WorkManager
-keep class androidx.work.impl.background.systemjob.SystemJobService
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver

# Glance widget
-keep class androidx.glance.appwidget.** { *; }
-keep class com.sdai.news.widget.** { *; }

# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**
