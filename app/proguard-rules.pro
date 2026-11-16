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
# Cloud Firestore reads and writes these classes by reflection, matching each document field to
# a property by name (toObject(...) and set(...)). The shrinker cannot see that, so without
# these rules it renames the properties and removes their accessors, and documents then map to
# empty objects in release builds only.
-keep class com.indeavour.coreader.model.firebase.** { *; }

# Firestore also needs the declared element types of collections such as Map<String, BookModel>,
# which are only recorded in these attributes.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
