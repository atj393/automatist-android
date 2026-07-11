# ── Automatist R8 / ProGuard Rules ────────────────────────────────────

# ── Kotlin Serialization ──────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.automatist.app.**$$serializer { *; }
-keepclassmembers class com.automatist.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.automatist.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit + Gson ───────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions

# Retain Retrofit service interfaces
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson model classes used by Retrofit converter
-keep class com.automatist.app.data.providers.openai.** { *; }
-keep class com.automatist.app.data.providers.anthropic.** { *; }
-keep class com.automatist.app.data.providers.gemini.** { *; }

# Prevent R8 from stripping Gson's TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Room ──────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Hilt ──────────────────────────────────────────────────────────────
# Hilt generates code that R8 usually handles, but keep entry points
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Google Drive API ──────────────────────────────────────────────────
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.json.gson.** { *; }
-dontwarn com.google.api.client.http.**
-dontwarn org.apache.http.**
-dontwarn com.google.android.gms.**

# ── AutoValue / JavaPoet annotation-processor classes (compile-only, not runtime) ─
# These leak into runtime classpath via google-api-client transitive deps.
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8

# ── Google Play Services Auth ─────────────────────────────────────────
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── OkHttp ────────────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── WorkManager ───────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Compose (generally handled by R8 defaults, but be safe) ───────────
-dontwarn androidx.compose.**

# ── MediaPipe LLM Inference (downloadable offline models) ─────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ── Google AI Edge AICore (Gemini Nano, on-device) ────────────────────
-keep class com.google.ai.edge.aicore.** { *; }
-dontwarn com.google.ai.edge.aicore.**

# ── Strip verbose logging in release ──────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
