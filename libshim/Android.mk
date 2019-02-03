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


# camera

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    VectorImpl.cpp

LOCAL_C_INCLUDES += \
    external/safe-iop/include

LOCAL_SHARED_LIBRARIES := liblog libutils
LOCAL_MODULE := libshim_camera
LOCAL_MODULE_TAGS := optional


include $(BUILD_SHARED_LIBRARY)


# wvm

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    VectorImpl.cpp

LOCAL_C_INCLUDES += \
    external/safe-iop/include

LOCAL_SHARED_LIBRARIES := liblog libutils
LOCAL_MODULE := libshim_wvm
LOCAL_MODULE_TAGS := optional


include $(BUILD_SHARED_LIBRARY)


# audio

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    VectorImpl.cpp

LOCAL_C_INCLUDES += \
    external/safe-iop/include

LOCAL_SHARED_LIBRARIES := liblog libutils
LOCAL_MODULE := libshim_audio
LOCAL_MODULE_TAGS := optional


include $(BUILD_SHARED_LIBRARY)


# RIL

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    ifc_utils.c

LOCAL_SHARED_LIBRARIES := liblog libcutils libnetutils
LOCAL_MODULE := libshim_ril
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
