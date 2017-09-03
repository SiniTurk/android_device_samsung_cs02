LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := libshim_wvm.cpp
LOCAL_SHARED_LIBRARIES := libstagefright_foundation
LOCAL_MODULE := libshim_wvm
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := libshim_glgps.cpp
LOCAL_MODULE := libshim_glgps
LOCAL_MODULE_TAGS := optional
LOCAL_SHARED_LIBRARIES := \
	libgui \
	libutils
LOCAL_C_INCLUDES += \
	frameworks/native/include/
include $(BUILD_SHARED_LIBRARY)
