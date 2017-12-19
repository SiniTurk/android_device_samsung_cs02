LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := libshim.cpp
LOCAL_MODULE := libshim
LOCAL_MODULE_TAGS := optional
LOCAL_SHARED_LIBRARIES := \
    libutils \
    libbinder \
    libgui \
    libstagefright_foundation \
    libmedia
LOCAL_C_INCLUDES := \
    frameworks/native/include/
include $(BUILD_SHARED_LIBRARY)
