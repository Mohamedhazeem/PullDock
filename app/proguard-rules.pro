# Keep public API surfaces and generic ProGuard rules.
# Feature-specific keep rules belong next to the code they protect.

-keepattributes *Annotation*
-dontwarn org.jetbrains.annotations.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Hilt / Dagger generated code
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
