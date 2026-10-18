#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <sstream>
#include <string>
#include "nnapi_runtime.h"

namespace {
struct Device;
std::string quoted(const char* value) {
    std::ostringstream out;
    out << '"';
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(value ? value : ""); *p; ++p) {
        if (*p == '"' || *p == '\\') out << '\\' << *p;
        else if (*p < 32) out << '?';
        else out << *p;
    }
    out << '"'; return out.str();
}
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_limelight_neural_NnapiDiagnostics_nativeSelfTest(JNIEnv* env, jclass) {
    occultation::NnapiRuntime runtime;
    return env->NewStringUTF(runtime.selfTest().c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_limelight_neural_NeuralHardwareReport_nativeDevices(JNIEnv* env, jclass) {
    void* lib = dlopen("libneuralnetworks.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) return env->NewStringUTF("NNAPI library is unavailable.\n");
    auto countFn = reinterpret_cast<int (*)(uint32_t*)>(dlsym(lib, "ANeuralNetworks_getDeviceCount"));
    auto deviceFn = reinterpret_cast<int (*)(uint32_t, Device**)>(dlsym(lib, "ANeuralNetworks_getDevice"));
    auto nameFn = reinterpret_cast<int (*)(const Device*, const char**)>(dlsym(lib, "ANeuralNetworksDevice_getName"));
    auto versionFn = reinterpret_cast<int (*)(const Device*, const char**)>(dlsym(lib, "ANeuralNetworksDevice_getVersion"));
    auto typeFn = reinterpret_cast<int (*)(const Device*, int32_t*)>(dlsym(lib, "ANeuralNetworksDevice_getType"));
    auto levelFn = reinterpret_cast<int (*)(const Device*, int64_t*)>(dlsym(lib, "ANeuralNetworksDevice_getFeatureLevel"));
    std::ostringstream out;
    uint32_t count = 0;
    if (!countFn || !deviceFn || !nameFn || !typeFn || !levelFn || !versionFn) {
        out << "NNAPI device enumeration requires Android 10 or newer.\n";
    } else if (countFn(&count) != 0) {
        out << "NNAPI device enumeration failed.\n";
    } else {
        out << "NNAPI devices reported: " << count << '\n';
        for (uint32_t i = 0; i < count; ++i) {
            Device* device = nullptr; const char* name = nullptr; const char* version = nullptr;
            int32_t type = 0; int64_t level = 0;
            if (deviceFn(i, &device) != 0 || !device || nameFn(device, &name) != 0 ||
                typeFn(device, &type) != 0 || levelFn(device, &level) != 0 || versionFn(device, &version) != 0) {
                out << "Device " << i << ": query failed\n"; continue;
            }
            const char* kind = type == 2 ? "CPU" : type == 3 ? "GPU" : type == 4 ? "ACCELERATOR" : "OTHER/UNKNOWN";
            out << "Device " << i << ": " << quoted(name) << ", type=" << kind
                << ", featureLevel=" << level << ", driver=" << quoted(version) << '\n';
        }
    }
    dlclose(lib);
    out << "Enumeration does not verify model compatibility, actual delegation, TPU execution or frame latency.\n";
    return env->NewStringUTF(out.str().c_str());
}
