$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_full.mk)

# The gps config appropriate for this device
$(call inherit-product, device/common/gps/gps_us_supl.mk)

$(call inherit-product-if-exists, vendor/samsung/cs02/cs02-vendor.mk)

DEVICE_PACKAGE_OVERLAYS += device/samsung/cs02/overlay

ifeq ($(TARGET_PREBUILT_KERNEL),)
	LOCAL_KERNEL := device/samsung/cs02/kernel
else
	LOCAL_KERNEL := $(TARGET_PREBUILT_KERNEL)
endif

PRODUCT_COPY_FILES += \
    $(LOCAL_KERNEL):kernel

$(call inherit-product, build/target/product/full.mk)

PRODUCT_BUILD_PROP_OVERRIDES += BUILD_UTC_DATE=0
PRODUCT_NAME := full_cs02
PRODUCT_DEVICE := cs02


LOCAL_PATH := device/samsung/cs02

# Use high-density artwork where available
PRODUCT_LOCALES += hdpi
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := hdpi

# Insecure ADBD
# (ro.adb.secure=3)
ADDITIONAL_DEFAULT_PROPERTIES += \
	ro.adb.secure=0 \
persist.service.adb.enable=1

# Base/Init Files
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/ramdisk/charger:root/charger \
    $(LOCAL_PATH)/ramdisk/fstab.hawaii_ss_cs02:root/fstab.hawaii_ss_cs02 \
    $(LOCAL_PATH)/ramdisk/init.bcm2166x.usb.rc:root/init.bcm2166x.usb.rc \
    $(LOCAL_PATH)/ramdisk/init.hawaii_ss_cs02.rc:root/init.hawaii_ss_cs02.rc \
    $(LOCAL_PATH)/ramdisk/init.log.rc:root/init.log.rc \
    $(LOCAL_PATH)/ramdisk/init.recovery.hawaii_ss_cs02.rc:root/init.recovery.hawaii_ss_cs02.rc \
    $(LOCAL_PATH)/ramdisk/lpm.rc:root/lpm.rc \
    $(LOCAL_PATH)/ramdisk/init.rc:root/init.rc \
    $(LOCAL_PATH)/ramdisk/ueventd.hawaii_ss_cs02.rc:root/ueventd.hawaii_ss_cs02.rc
