# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames

# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose

# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses

# 避免混淆泛型
-keepattributes Signature

# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*

-flattenpackagehierarchy

#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################
# 屏蔽错误Unresolved class name
#noinspection ShrinkerUnresolvedReference

# 移除Log类打印各个等级日志的代码，打正式包的时候可以做为禁log使用，这里可以作为禁止log打印的功能使用
# 记得proguard-android.txt中一定不要加-dontoptimize才起作用
# 另外的一种实现方案是通过BuildConfig.DEBUG的变量来控制
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 保持js引擎调用的java类
-keep class * extends io.legado.app.help.JsExtensions{*;}
# 数据类
-keep class **.data.entities.**{*;}
# Gson反序列化用的数据传输类
-keep class io.legado.app.model.translation.**{*;}
# io.legado.app.domain.model 整包保留：
# 该包的数据类原先逐个用 androidx.annotation.Keep 标注（共 33 处，含嵌套类），
# 下沉到 :core:model 的 commonMain 后拿不到 androidx.annotation，改由本规则统一兜底。
# 用整包规则而非逐类枚举，是为了避免漏掉任何一个曾经被 @Keep 保护的嵌套类——
# 漏保会导致 release 包 Gson 反序列化静默失败，而 debug 与单测都测不出来。
# 代价只是略微放弃该包的裁剪收益，与上面 data.entities 的做法一致。
# 注意：这些类虽已移入 :core:model，包名未变，按 FQCN 匹配的规则仍然生效。
-keep class io.legado.app.domain.model.**{*;}
-keep class io.legado.app.data.repository.GoogleTranslateResponse{*;}
-keep class io.legado.app.data.repository.GoogleSentence{*;}
-keep class io.legado.app.data.repository.GoogleSpell{*;}
-keep class io.legado.app.data.repository.OpenAIResponse{*;}
-keep class io.legado.app.data.repository.OpenAIChoice{*;}
-keep class io.legado.app.data.repository.OpenAIMessage{*;}
# 缓存 Cookie
-keep class **.help.http.CookieStore{*;}
-keep class **.help.CacheManager{*;}
# StrResponse
-keep class **.help.http.StrResponse{*;}

# markwon
-dontwarn org.commonmark.ext.gfm.**

# hutool-core hutool-crypto（书源 JS 通过 Packages.cn.hutool.* 反射调用，需整体保留）
-keep class cn.hutool.crypto.**{*;}
-keep class cn.hutool.core.codec.**{*;}
-keep class cn.hutool.core.util.**{*;}
-dontwarn cn.hutool.**

-keep class okhttp3.*{*;}
-keep class okio.*{*;}
-keep class com.jayway.jsonpath.*{*;}

# LiveEventBus
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

## ChangeBookSourceDialog initNavigationView
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}

# FileDocExtensions.kt treeDocumentFileConstructor
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# JsoupXpath
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.AxisSelector{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.NodeTest{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.Function{*;}

## JSOUP
-keep class org.jsoup.**{*;}
-dontwarn org.jspecify.annotations.NullMarked

## ExoPlayer 反射设置ua 保证该私有变量不被混淆
-keepclassmembers class androidx.media3.datasource.cache.CacheDataSource$Factory {
    *** upstreamDataSourceFactory;
}
## ExoPlayer 如果还不能播放就取消注释这个
# -keep class com.google.android.exoplayer2.** {*;}

## 对外提供api
-keep class io.legado.app.api.ReturnData{*;}

# Cronet
-keepclassmembers class org.chromium.net.X509Util {
    *** sDefaultTrustManager;
    *** sTestTrustManager;
}

# Throwable
-keepnames class * extends java.lang.Throwable
-keepclassmembernames,allowobfuscation class * extends java.lang.Throwable{*;}
# 忽略 Ktor 在 Android 上对 Java SE 管理类的引用
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.IntellijIdeaDebugDetector
-keep,allowobfuscation class io.ktor.util.debug.** { *; }
