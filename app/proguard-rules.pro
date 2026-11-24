# Keep JNI names for the optional llama.cpp bridge.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.brahma.mobile.brain.LlamaEngine { *; }
