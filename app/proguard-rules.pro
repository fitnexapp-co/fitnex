# kotlinx.serialization: keep generated serializers (type-safe navigation routes, network DTOs).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit + coroutines under R8 full mode. Without these, the generic return types of the
# suspend API methods are erased: Retrofit then asks for a converter for java.lang.Object and
# throws "Serializer for class 'Any' is not found" (seen on a real Galaxy A17, v1.3.0).
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
# FITNEX backend API and its DTOs: names and generic signatures stay intact.
-keep interface com.fitnex.core.network.FitnexBackendApi { *; }
-keep @kotlinx.serialization.Serializable class com.fitnex.core.network.** { *; }