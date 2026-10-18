LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := occultation-neural-probe
LOCAL_SRC_FILES := neural_probe.cpp
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Werror
LOCAL_LDLIBS := -ldl
include $(BUILD_SHARED_LIBRARY)
