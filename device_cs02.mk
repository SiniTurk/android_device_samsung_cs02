$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_full.mk)

$(call inherit-product, vendor/samsung/cs02/cs02-vendor.mk)

# Use high-density artwork where available
PRODUCT_LOCALES += hdpi
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := hdpi

DEVICE_PACKAGE_OVERLAYS += device/samsung/cs02/overlay

# Base/Init Files
PRODUCT_COPY_FILES += \
    device/samsung/cs02/ramdisk/fstab.hawaii_ss_cs02:root/fstab.hawaii_ss_cs02 \
    device/samsung/cs02/ramdisk/init.hawaii_ss_cs02.rc:root/init.hawaii_ss_cs02.rc \
    device/samsung/cs02/ramdisk/init.bcm2166x.usb.rc:root/init.bcm2166x.usb.rc \
    device/samsung/cs02/ramdisk/init.log.rc:root/init.log.rc \
    device/samsung/cs02/ramdisk/charger:root/charger \
    device/samsung/cs02/ramdisk/ueventd.hawaii_ss_cs02.rc:root/ueventd.hawaii_ss_cs02.rc

PRODUCT_COPY_FILES += \
    device/samsung/cs02/configs/media_profiles.xml:system/etc/media_profiles.xml \
    device/samsung/cs02/configs/audio_policy.conf:system/etc/audio_policy.conf \
    frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml:system/etc/media_codecs_google_audio.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:system/etc/media_codecs_google_telephony.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_video_le.xml:system/etc/media_codecs_google_video_le.xml \
    device/samsung/cs02/configs/media_codecs.xml:system/etc/media_codecs.xml 

# Prebuilt kl keymaps
PRODUCT_COPY_FILES += \
    device/samsung/cs02/keylayouts/bcm_headset.kl:system/usr/keylayout/bcm_headset.kl \
    device/samsung/cs02/keylayouts/bcm_keypad_v2.kl:system/usr/keylayout/bcm_keypad_v2.kl \
    device/samsung/cs02/keylayouts/gpio-keys.kl:system/usr/keylayout/gpio-keys.kl \
    device/samsung/cs02/keylayouts/samsung-keypad.kl:system/usr/keylayout/samsung-keypad.kl

# Charger
PRODUCT_PACKAGES += \
    charger_res_images \
    cm_charger_res_images

# Insecure ADBD
ADDITIONAL_DEFAULT_PROPERTIES += \
    ro.adb.secure=0 \
    ro.secure=0 \
    persist.service.adb.enable=1

# Filesystem management tools
PRODUCT_PACKAGES += \
    make_ext4fs \
    e2fsck \
    setup_fs

# IPv6 tethering
PRODUCT_PACKAGES += \
    ebtables \
    ethertypes

# Misc other modules
PRODUCT_PACKAGES += \
    audio.a2dp.default \
    audio.usb.default \
    audio.r_submix.default \
    audio.primary.default

# Gello
PRODUCT_PACKAGES += \
    Gello

# KSM
PRODUCT_PROPERTY_OVERRIDES += \
    ro.ksm.default=0

# Wi-Fi
PRODUCT_PACKAGES += \
    macloader \
    dhcpcd.conf \
    hostapd \
    libnetcmdiface \
    wpa_supplicant \
    wpa_supplicant.conf

# GPS/RIL
PRODUCT_PACKAGES += \
    libstlport \
    libsecril-client

# Shim lib
PRODUCT_PACKAGES += \
    libshim

# These are the hardware-specific features
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:system/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.location.xml:system/etc/permissions/android.hardware.location.xml \
    frameworks/native/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:system/etc/permissions/android.hardware.ethernet.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:system/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:system/etc/permissions/android.hardware.sensor.compass.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:system/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/native/data/etc/android.software.sip.voip.xml:system/etc/permissions/android.software.sip.voip.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \

# These are the hardware-specific settings that are stored in system properties.
# Note that the only such settings should be the ones that are too low-level to
# be reachable from resources or other mechanisms.
PRODUCT_PROPERTY_OVERRIDES += \
    wifi.interface=wlan0 \
    mobiledata.interfaces=rmnet0 \
    ro.telephony.ril_class=SamsungBCMRIL \
    persist.radio.multisim.config=none \
    ro.telephony.call_ring.multiple=0 \
    camera2.portability.force_api=1 \
    ro.telephony.call_ring=0

# MTP
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mtp

# Dalvik heap config
$(call inherit-product, frameworks/native/build/phone-hdpi-512-dalvik-heap.mk)

# Texture config.
$(call inherit-product, frameworks/native/build/phone-xxhdpi-2048-hwui-memory.mk)

$(call inherit-product, hardware/broadcom/wlan/bcmdhd/config/config-bcm.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

PRODUCT_BUILD_PROP_OVERRIDES += BUILD_UTC_DATE=0
PRODUCT_NAME := lineage_cs02
PRODUCT_DEVICE := cs02
PRODUCT_MODEL := SM-G350
