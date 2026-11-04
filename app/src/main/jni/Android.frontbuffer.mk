# Resolve this makefile's directory independently of caller LOCAL_PATH.
FRONTBUFFER_THIS_MK := $(abspath $(lastword $(MAKEFILE_LIST)))
FRONTBUFFER_DIR := $(patsubst %/,%,$(dir $(FRONTBUFFER_THIS_MK)))
LOCAL_PATH := $(FRONTBUFFER_DIR)

include $(CLEAR_VARS)
LOCAL_MODULE := occultation_frontbuffer
LOCAL_SRC_FILES := front_buffer_native.cpp
LOCAL_CPPFLAGS := -std=c++17 -O2 -fvisibility=hidden
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv2 -ldl
include $(BUILD_SHARED_LIBRARY)
