# 星简助手混淆规则
# Room 生成类
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# JavaMail (Android 版本)
-dontwarn javax.mail.**
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
