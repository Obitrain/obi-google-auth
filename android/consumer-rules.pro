# Nitro constructs the hybrid object from C++ via JNI; without this R8 assumes it is never
# instantiated and folds its constructor-initialized fields (executor, mainHandler) to null
-keep class com.margelo.nitro.obitrain.reactnativegoogleauth.ReactNativeGoogleAuth { <init>(); }
