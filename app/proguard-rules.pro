-keep class dev.shizzi.TetherService { *; }

-keep interface dev.shizzi.ITetherService { *; }
-keep class dev.shizzi.ITetherService$* { *; }

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

-keep class dev.shizzi.App
-keep class dev.shizzi.MainActivity
-keep class dev.shizzi.SessionService

-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Go datapath bindings (gomobile): the native library looks these classes up by name over JNI.
-keep class go.** { *; }
-keep class datapath.** { *; }
-dontwarn go.**
-dontwarn datapath.**
