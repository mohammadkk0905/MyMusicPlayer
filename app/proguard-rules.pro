# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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
###############
# jaudiotagger
################
-keepclasseswithmembers enum org.jaudiotagger.* { public java.lang.String name(); }
-keepclasseswithmembers class org.jaudiotagger.tag.id3.framebody.Framebody** {<init>(...);}
-keepclasseswithmembers class org.jaudiotagger.tag.** extends org.jaudiotagger.tag.id3.AbstractTagItem {<init>(...);}
-keepclasseswithmembers class org.jaudiotagger.tag.datatype.** extends org.jaudiotagger.tag.datatype.AbstractDataType {<init>(...);}
-keepclasseswithmembers class org.jcodec.containers.mp4.boxes.** {<init>(...);}