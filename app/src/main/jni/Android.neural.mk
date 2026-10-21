LOCAL_PATH := $(APP_JNI_DIR)
include $(CLEAR_VARS)
LOCAL_MODULE := occultation-neural-probe
LOCAL_SRC_FILES := neural_probe.cpp nnapi_runtime.cpp
# Public compatibility backend: NNAPI is deprecated but loaded only when available.
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Werror -Wno-deprecated-declarations
LOCAL_LDLIBS := -ldl
include $(BUILD_SHARED_LIBRARY)
