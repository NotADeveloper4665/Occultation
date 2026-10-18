# Pinned SDK/source files are prepared by the Gradle prepareRife task.
RIFE_GENERATED := $(APP_JNI_DIR)/../../../build/interpolation
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_ncnn
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libncnn.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_SPIRV
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libSPIRV.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_glslang
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libglslang.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_MachineIndependent
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libMachineIndependent.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_OSDependent
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libOSDependent.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_GenericCodeGen
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libGenericCodeGen.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_OGLCompiler
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libOGLCompiler.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := rife_glslang-default-resource-limits
LOCAL_SRC_FILES := ../../../build/interpolation/ncnn/$(TARGET_ARCH_ABI)/lib/libglslang-default-resource-limits.a
include $(PREBUILT_STATIC_LIBRARY)
LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := occultation-rife-vulkan
LOCAL_SRC_FILES := rife_jni.cpp ../../../build/interpolation/rife/rife.cpp ../../../build/interpolation/rife/warp.cpp
LOCAL_C_INCLUDES := $(RIFE_GENERATED)/rife $(RIFE_GENERATED)/ncnn/$(TARGET_ARCH_ABI)/include/ncnn
LOCAL_CPPFLAGS := -std=c++17 -O2 -fno-exceptions -fno-rtti
LOCAL_LDFLAGS := -fopenmp -static-openmp
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_GROUP_STATIC_LIBRARIES := true
LOCAL_STATIC_LIBRARIES := rife_ncnn rife_SPIRV rife_glslang rife_MachineIndependent rife_OSDependent rife_GenericCodeGen rife_OGLCompiler rife_glslang-default-resource-limits
include $(BUILD_SHARED_LIBRARY)
