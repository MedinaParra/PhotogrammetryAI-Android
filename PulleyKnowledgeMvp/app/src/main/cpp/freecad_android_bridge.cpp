#include <jni.h>
#include <string>

namespace {
thread_local std::string last_error;
jstring text(JNIEnv* env,const std::string& value){return env->NewStringUTF(value.c_str());}
}

extern "C" JNIEXPORT jstring JNICALL
Java_cl_skm_pulleyai_FreeCadNativeBridge_nativeRuntimeInfo(JNIEnv* env,jclass){
#ifdef FREECAD_OCC_AVAILABLE
    return text(env,"FreeCAD/OpenCascade Android bridge 1.0");
#else
    return text(env,"FreeCAD Android JNI bridge 1.0; OpenCascade kernel not linked");
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_cl_skm_pulleyai_FreeCadNativeBridge_nativeCapabilitiesMask(JNIEnv*,jclass){
#ifdef FREECAD_OCC_AVAILABLE
    return (1LL<<0)|(1LL<<1)|(1LL<<2)|(1LL<<3)|(1LL<<4);
#else
    return 0;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_cl_skm_pulleyai_FreeCadNativeBridge_nativeLastError(JNIEnv* env,jclass){
#ifndef FREECAD_OCC_AVAILABLE
    if(last_error.empty())last_error="OpenCascade/FreeCAD STEP kernel is not linked in this build";
#endif
    return text(env,last_error);
}
