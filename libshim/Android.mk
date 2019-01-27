# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

# General libshim

include $(CLEAR_VARS)

LOCAL_SRC_FILES := libshim.cpp
LOCAL_MODULE := libshim
LOCAL_MODULE_TAGS := optional
LOCAL_SHARED_LIBRARIES := \
    libgui \
    libutils \
    libstagefright \
    libbinder
LOCAL_C_INCLUDES += \
    frameworks/native/include/ \
    frameworks/av/include/

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
