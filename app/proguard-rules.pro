-keep class com.resonance.recorder.data.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
