LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := occultation-video-display
LOCAL_SRC_FILES := display/ahb_vulkan.cpp
LOCAL_C_INCLUDES := $(APP_JNI_DIR)/../../../build/display
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Wno-missing-field-initializers -fexceptions -frtti
LOCAL_LDLIBS := -landroid -ldl -llog
include $(BUILD_SHARED_LIBRARY)
