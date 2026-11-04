#include <jni.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <cstdint>

#ifndef EGL_NATIVE_BUFFER_ANDROID
#define EGL_NATIVE_BUFFER_ANDROID 0x3140
#endif
#ifndef EGL_IMAGE_PRESERVED_KHR
#define EGL_IMAGE_PRESERVED_KHR 0x30D2
#endif

using GetNativeClientBufferFn = EGLClientBuffer (*)(const AHardwareBuffer*);
using CreateImageKhrFn = EGLImageKHR (*)(EGLDisplay, EGLContext, EGLenum, EGLClientBuffer, const EGLint*);
using DestroyImageKhrFn = EGLBoolean (*)(EGLDisplay, EGLImageKHR);
using ImageTargetRenderbufferFn = void (*)(GLenum, EGLImageKHR);

struct HardwareBufferFbo {
    AHardwareBuffer* buffer;
    EGLDisplay display;
    EGLImageKHR image;
    GLuint renderbuffer;
    GLuint framebuffer;
};

static void* resolveSymbol(const char* name) {
    void* symbol = dlsym(RTLD_DEFAULT, name);
    if (symbol == nullptr) {
        symbol = reinterpret_cast<void*>(eglGetProcAddress(name));
    }
    return symbol;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_limelight_binding_video_FrontBufferHardwareBridge_nativeCreateFbo(
        JNIEnv* env, jclass, jobject javaBuffer) {
    if (javaBuffer == nullptr) return 0;

    auto fromJava = reinterpret_cast<AHardwareBuffer* (*)(JNIEnv*, jobject)>(
            resolveSymbol("AHardwareBuffer_fromHardwareBuffer"));
    auto getNativeClientBuffer = reinterpret_cast<GetNativeClientBufferFn>(
            resolveSymbol("eglGetNativeClientBufferANDROID"));
    auto createImage = reinterpret_cast<CreateImageKhrFn>(
            resolveSymbol("eglCreateImageKHR"));
    auto imageTargetRenderbuffer = reinterpret_cast<ImageTargetRenderbufferFn>(
            resolveSymbol("glEGLImageTargetRenderbufferStorageOES"));

    if (fromJava == nullptr || getNativeClientBuffer == nullptr ||
            createImage == nullptr || imageTargetRenderbuffer == nullptr) {
        return 0;
    }

    AHardwareBuffer* buffer = fromJava(env, javaBuffer);
    if (buffer == nullptr) return 0;
    AHardwareBuffer_acquire(buffer);

    EGLDisplay display = eglGetCurrentDisplay();
    EGLContext context = eglGetCurrentContext();
    if (display == EGL_NO_DISPLAY || context == EGL_NO_CONTEXT) {
        AHardwareBuffer_release(buffer);
        return 0;
    }

    EGLClientBuffer clientBuffer = getNativeClientBuffer(buffer);
    if (clientBuffer == nullptr) {
        AHardwareBuffer_release(buffer);
        return 0;
    }

    const EGLint imageAttributes[] = {
            EGL_IMAGE_PRESERVED_KHR, EGL_TRUE,
            EGL_NONE
    };
    EGLImageKHR image = createImage(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
                                    clientBuffer, imageAttributes);
    if (image == EGL_NO_IMAGE_KHR) {
        AHardwareBuffer_release(buffer);
        return 0;
    }

    auto* result = new HardwareBufferFbo{buffer, display, image, 0, 0};
    glGenRenderbuffers(1, &result->renderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, result->renderbuffer);
    imageTargetRenderbuffer(GL_RENDERBUFFER, image);

    glGenFramebuffers(1, &result->framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, result->framebuffer);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_RENDERBUFFER, result->renderbuffer);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindRenderbuffer(GL_RENDERBUFFER, 0);

    if (status != GL_FRAMEBUFFER_COMPLETE) {
        glDeleteFramebuffers(1, &result->framebuffer);
        glDeleteRenderbuffers(1, &result->renderbuffer);
        auto destroyImage = reinterpret_cast<DestroyImageKhrFn>(
                resolveSymbol("eglDestroyImageKHR"));
        if (destroyImage != nullptr) destroyImage(display, image);
        AHardwareBuffer_release(buffer);
        delete result;
        return 0;
    }

    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_limelight_binding_video_FrontBufferHardwareBridge_nativeBindFbo(
        JNIEnv*, jclass, jlong handle) {
    auto* fbo = reinterpret_cast<HardwareBufferFbo*>(handle);
    if (fbo == nullptr) return JNI_FALSE;
    glBindFramebuffer(GL_FRAMEBUFFER, fbo->framebuffer);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE
            ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_limelight_binding_video_FrontBufferHardwareBridge_nativeUnbindFbo(
        JNIEnv*, jclass) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_limelight_binding_video_FrontBufferHardwareBridge_nativeDestroyFbo(
        JNIEnv*, jclass, jlong handle) {
    auto* fbo = reinterpret_cast<HardwareBufferFbo*>(handle);
    if (fbo == nullptr) return;

    if (fbo->framebuffer != 0) glDeleteFramebuffers(1, &fbo->framebuffer);
    if (fbo->renderbuffer != 0) glDeleteRenderbuffers(1, &fbo->renderbuffer);

    auto destroyImage = reinterpret_cast<DestroyImageKhrFn>(
            resolveSymbol("eglDestroyImageKHR"));
    if (destroyImage != nullptr && fbo->image != EGL_NO_IMAGE_KHR) {
        destroyImage(fbo->display, fbo->image);
    }
    if (fbo->buffer != nullptr) AHardwareBuffer_release(fbo->buffer);
    delete fbo;
}
