#include <jni.h>
#include <cmath>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <string>
#include "rife.h"
#include "gpu.h"

namespace {
std::mutex mutex;
std::once_flag instance;
struct Session { std::unique_ptr<RIFE> model; std::string device; };
std::unordered_map<jlong, Session> sessions;
jlong nextHandle = 1;
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_limelight_interpolation_VulkanRife_nativeOpen(JNIEnv* env, jclass, jstring directory) {
    if (!directory) { fail(env, "Missing RIFE model directory"); return 0; }
    const char* value = env->GetStringUTFChars(directory, nullptr);
    if (!value) return 0;
    const std::string path(value);
    env->ReleaseStringUTFChars(directory, value);
    if (path.size() > 180) { fail(env, "RIFE model path exceeds upstream path limit"); return 0; }
    std::lock_guard<std::mutex> lock(mutex);
    std::call_once(instance, [] { ncnn::create_gpu_instance(); });
    if (ncnn::get_gpu_count() <= 0) { fail(env, "No compatible Vulkan GPU exposed; CPU fallback disabled"); return 0; }
    const int gpu = ncnn::get_default_gpu_index();
    if (gpu < 0 || gpu >= ncnn::get_gpu_count()) { fail(env, "Invalid Vulkan device selection"); return 0; }
    Session session;
    session.device = ncnn::get_gpu_info(gpu).device_name();
    session.model.reset(new RIFE(gpu, false, false, false, 1, false, true));
    if (session.model->load(path) != 0) { fail(env, "RIFE Vulkan model failed to load"); return 0; }
    const jlong handle = nextHandle++;
    sessions.emplace(handle, std::move(session));
    return handle;
}
extern "C" JNIEXPORT void JNICALL
Java_com_limelight_interpolation_VulkanRife_nativeInterpolate(JNIEnv* env, jclass, jlong handle,
        jobject first, jobject second, jobject output, jint width, jint height, jfloat time) {
    if (width <= 0 || height <= 0 || width > 1920 || height > 1080 ||
            !std::isfinite(time) || time <= 0 || time >= 1) {
        fail(env, "Invalid RIFE RGB frame dimensions or timestep"); return;
    }
    const jlong bytes = static_cast<jlong>(width) * height * 3;
    void* a = env->GetDirectBufferAddress(first);
    void* b = env->GetDirectBufferAddress(second);
    void* c = env->GetDirectBufferAddress(output);
    if (!a || !b || !c || a == c || b == c ||
            env->GetDirectBufferCapacity(first) < bytes || env->GetDirectBufferCapacity(second) < bytes ||
            env->GetDirectBufferCapacity(output) < bytes) {
        fail(env, "RIFE requires separate direct RGB24 buffers of sufficient capacity"); return;
    }
    std::lock_guard<std::mutex> lock(mutex);
    auto session = sessions.find(handle);
    if (session == sessions.end()) { fail(env, "RIFE session is closed"); return; }
    ncnn::Mat input0(width, height, a, static_cast<size_t>(3), 3);
    ncnn::Mat input1(width, height, b, static_cast<size_t>(3), 3);
    ncnn::Mat result(width, height, c, static_cast<size_t>(3), 3);
    if (session->second.model->process_v4(input0, input1, time, result) != 0)
        fail(env, "RIFE Vulkan execution failed; generated frame must be discarded");
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_limelight_interpolation_VulkanRife_nativeDevice(JNIEnv* env, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(mutex);
    auto session = sessions.find(handle);
    if (session == sessions.end()) { fail(env, "RIFE session is closed"); return nullptr; }
    return env->NewStringUTF(session->second.device.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_limelight_interpolation_VulkanRife_nativeClose(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(mutex);
    sessions.erase(handle);
}
