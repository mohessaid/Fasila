# JNA and the UniFFI-generated bindings are reached reflectively from native code.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class uniffi.fasila_ffi.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
