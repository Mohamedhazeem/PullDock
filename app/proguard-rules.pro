# Keep public API surfaces and generic ProGuard rules.
# Feature-specific keep rules belong next to the code they protect.

-keepattributes *Annotation*
-keepattributes Signature, InnerClasses, EnclosingMethod
-dontwarn org.jetbrains.annotations.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Coroutines: debuggability of coroutine machinery under R8.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Jetpack Compose: the Compose compiler ships its own consumer rules; keep the
# runtime-applied annotations that tooling and previews reflect on.
-dontwarn androidx.compose.**

# Room: keep database subclasses and entity/DAO annotations intact for reflection
# used by the runtime during invalidation tracking.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# DataStore: serializer classes are accessed reflectively.
-if @androidx.datastore.preferences.core.PreferencesKeys* class *
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Native engines (Slices 3/4): keep the JNI surface so native code can find its
# entry points. libtorrent4j, the Python bridge, and FFmpeg wrappers add their own
# rules when their dependency lands (T060/T080) — these are the baseline.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Chaquopy / Python runtime: the Python bridge is reached reflectively.
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# libtorrent4j (SWIG JNI): the native layer calls back into these classes/methods
# by name; R8 must not rename or remove them.
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# FFmpegKit (extractor fallback remux, ADR-0013): keep the JNI-facing API and ignore
# its optional smart-exception dependency, which is not bundled.
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.arthenica.smartexception.**
