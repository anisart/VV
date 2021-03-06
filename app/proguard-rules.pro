# General
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn javax.annotation.**

# App classes
-keep class ru.anisart.vv.** { *; }

# Javascript
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

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