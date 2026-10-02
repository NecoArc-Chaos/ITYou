# ---------------------------------------------------------------------------
# ITYou Release R8 规则
# ---------------------------------------------------------------------------

# ---- OkHttp / Conscrypt / BouncyCastle 可选依赖 ----
# OkHttp 在部分平台上会尝试加载这些可选实现，缺失时无需告警。
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ---- Kotlin 协程 ----
-dontwarn kotlinx.coroutines.**

# ---- Jsoup ----
# Jsoup 通过资源文件加载 HTML 实体表，并对部分节点使用反射式访问，
# 保留其包内符号以避免 R8 过度裁剪导致的运行时解析失败。
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
# Jsoup 1.18+ 在 package-info / 内部类使用了 jspecify 可选注解，
# 编译期不会打包该依赖，R8 默认会报错，声明 dontwarn 忽略即可。
-dontwarn org.jspecify.annotations.**

# ---- Coil 图片加载 ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- 数据模型 ----
# 保留数据类的字段名与结构，便于调试与后续引入序列化时保持兼容。
-keepclassmembers class com.necoarc.ityou.data.model.** {
    <fields>;
    <init>(...);
}

# ---- 保留行号信息，便于线上崩溃定位（配合 mapping.txt） ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
