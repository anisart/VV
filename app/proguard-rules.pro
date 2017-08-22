# General
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn javax.annotation.**

# Javascript
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Retrofit
-dontnote retrofit2.Platform # Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform$IOS$MainThreadExecutor # Platform used when running on RoboVM on iOS. Will not be used at runtime.
-dontwarn retrofit2.Platform$Java8 # Platform used when running on Java 8 VMs. Will not be used at runtime.

# OkHttp 3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Okio
-keep class sun.misc.Unsafe { *; }
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**

# App compat
-keep public class android.support.v7.widget.** { *; }
-keep public class android.support.v7.internal.widget.** { *; }
-keep public class android.support.v7.internal.view.menu.** { *; }
-keep public class * extends android.support.v4.view.ActionProvider {
    public <init>(android.content.Context);
}

# Butterknife
-keep public class * implements butterknife.Unbinder { public <init>(**, android.view.View); }
-keep class butterknife.*
-keepclasseswithmembernames class * { @butterknife.* <methods>; }
-keepclasseswithmembernames class * { @butterknife.* <fields>; }

# Joda time
-dontwarn org.joda.convert.**
-dontwarn org.joda.time.**
-keep class org.joda.time.** { *; }
-keep interface org.joda.time.** { *; }

# Unity
-dontwarn com.androidnative.**
-keep public class com.unity.plugins.** { *; }
-keep class com.unity3d.** { *; }
-keep class bitter.jnibridge.** { *; }
-keep class org.fmod.** { *; }

# Billing
-keep class com.android.vending.billing.**

# Mapbox
#-keep class com.mapbox.services.api.** { *; }
#-keep class com.mapbox.services.api.directions.v5.models.** { *; }
#-keep class com.mapbox.services.api.distance.v1.models.** { *; }
#-keep class com.mapbox.services.api.geocoding.v5.** { *; }
#-keep class com.mapbox.services.api.geocoding.v5.models.** { *; }
#-keep class com.mapbox.services.api.mapmatching.v5.models.** { *; }
#-keep class com.mapbox.services.api.optimizedtrips.v1.models.** { *; }
#-keep class com.mapbox.services.api.directionsmatrix.v1.models.** { *; }
#-keep class com.mapbox.services.api.utils.turf.** { *; }
#-keep class com.mapbox.services.commons.geojson.** { *; }

# Google
-keep class com.google.**
-dontwarn com.google.**