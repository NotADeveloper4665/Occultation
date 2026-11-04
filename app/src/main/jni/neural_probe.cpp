#include <jni.h>
#include <string>
#include "nnapi_runtime.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_limelight_neural_NnapiDiagnostics_nativeSelfTest(JNIEnv* env, jclass) {
    occultation::NnapiRuntime runtime;
    const std::string result = runtime.selfTest();
    return env->NewStringUTF(result.c_str());
}
