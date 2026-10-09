# R8 configuration for release builds.
#
# Goal: satisfy Play's DEX optimization checks (shrinking/obfuscation) while keeping
# crash reports readable. Our own code keeps its real class/method names and line
# numbers, so stack traces emailed by CustomExceptionHandler look just like before.
# Only library code gets renamed; decode those frames with R8 retrace and
# app/build/outputs/mapping/release/mapping.txt for that version, if ever needed.

# --- Readable stack traces for our code -----------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepnames class com.myflightbook.** { *; }
-keepnames class model.** { *; }

# --- ksoap2 (web services) -------------------------------------------------------
# libs/ksoap2-android-assembly-3.6.2-jar-with-dependencies.jar bundles ksoap2, kxml2, kobjects,
# and an old OkHttp 3 + Okio (which CustomExceptionHandler also uses). It's reflection-heavy and
# ships no rules, so keep all of it as-is.
# NOTE: org/xmlpull/** was removed from that jar: Android provides org.xmlpull.v1 itself (the
# platform copy always wins at runtime), and R8 fails the build on the duplicate. If the jar is
# ever replaced, strip org/xmlpull/ from the new one too.
-keep class org.ksoap2.** { *; }
-keep class org.kxml2.** { *; }
-keep class org.kobjects.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn org.ksoap2.**
-dontwarn org.kxml2.**
-dontwarn org.kobjects.**
-dontwarn org.xmlpull.**
-dontwarn okhttp3.**
-dontwarn okio.**
# Optional platforms referenced by old OkHttp, not present on Android.
-dontwarn org.eclipse.jetty.**
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# SoapSerializationEnvelope.addMapping() creates objects with Class.newInstance(),
# so mapped model classes need their no-arg constructors.
-keepclassmembers class model.** { <init>(); }
-keepclassmembers class com.myflightbook.** { <init>(); }

# Enum values are parsed from server strings / prefs with valueOf(name).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Java serialization ----------------------------------------------------------
# Objects are serialized for intent extras, cloning, and the home-screen widgets'
# cached data (MFBUtil.serializeToString).
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Other reflection ------------------------------------------------------------
# MFBMain.hasMaps() probes for this class by name; if R8 removed it, map features
# would silently disappear.
-keep class com.google.android.gms.maps.MapFragment

# FragmentHostActivity instantiates fragments by class name.
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }
