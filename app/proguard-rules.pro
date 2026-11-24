# kotlinx.serialization, OkHttp and Coil ship their own consumer rules.
# Keep @Serializable model classes readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ZXing: only the QR writer is used, everything else can be stripped.
-dontwarn com.google.zxing.**
