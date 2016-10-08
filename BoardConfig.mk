USE_CAMERA_STUB := true

# inherit from the proprietary version
-include vendor/samsung/cs02/BoardConfigVendor.mk

TARGET_ARCH := arm
TARGET_NO_BOOTLOADER := true
TARGET_BOARD_PLATFORM := hawaii
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_VARIANT := cortex-a9
TARGET_CPU_SMP := true
ARCH_ARM_HAVE_TLS_REGISTER := true
TARGET_BOOTLOADER_BOARD_NAME := hawaii
TARGET_GLOBAL_CFLAGS += -mtune=cortex-a9 -mfpu=neon -mfloat-abi=softfp
TARGET_GLOBAL_CPPFLAGS += -mtune=cortex-a9 -mfpu=neon -mfloat-abi=softfp

# Assert
TARGET_OTA_ASSERT_DEVICE := cs02,G350,SM-G350,hawaii

# Kernel
BOARD_KERNEL_BASE := 0x82000000
BOARD_KERNEL_PAGESIZE := 4096
TARGET_PREBUILT_KERNEL := device/samsung/cs02/kernel
TARGET_KERNEL_CONFIG := bcm21664_hawaii_ss_cs02_rev02_defconfig
TARGET_KERNEL_SOURCE := kernel/samsung/cs02/
# Make sure you have the correct toolchain available
TARGET_KERNEL_CUSTOM_TOOLCHAIN := arm-eabi-4.6

# Partition sizes
BOARD_BOOTIMAGE_PARTITION_SIZE := 8388608 # mmcblk0p5, 8192 blocks
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 8388608 # mmcblk0p6, 8192 blocks
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1210769408 # mmcblk0p17, 1182392 blocks
BOARD_USERDATAIMAGE_PARTITION_SIZE := 2373976064 # mmcblk0p19, 2318336 blocks
BOARD_CACHEIMAGE_PARTITION_SIZE := 209715200
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_FLASH_BLOCK_SIZE := 1024

# Recovery
TARGET_RECOVERY_FSTAB := device/samsung/cs02/ramdisk/fstab.hawaii_ss_cs02
BOARD_HAS_NO_SELECT_BUTTON := true

# SELinux
BOARD_SEPOLICY_DIRS += \
    device/samsung/cs02/sepolicy

# Connectivity - Wi-Fi
BOARD_HAVE_SAMSUNG_WIFI                     := true
BOARD_WLAN_DEVICE                           := bcmdhd
BOARD_WLAN_DEVICE_REV                       := bcm4330_b1
WPA_BUILD_SUPPLICANT                        := true
BOARD_WPA_SUPPLICANT_DRIVER                 := NL80211
WPA_SUPPLICANT_VERSION                      := VER_0_8_X
BOARD_WPA_SUPPLICANT_PRIVATE_LIB            := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
BOARD_HOSTAPD_DRIVER                        := NL80211
BOARD_HOSTAPD_PRIVATE_LIB                   := lib_driver_cmd_$(BOARD_WLAN_DEVICE)
WIFI_DRIVER_FW_PATH_PARAM                   := "/sys/module/dhd/parameters/firmware_path"
WIFI_DRIVER_FW_PATH_STA                     := "/system/etc/wifi/bcmdhd_sta.bin"
WIFI_DRIVER_FW_PATH_AP                      := "/system/etc/wifi/bcmdhd_apsta.bin"
WIFI_DRIVER_FW_PATH_P2P                     := "/system/etc/wifi/bcmdhd_p2p.bin"
WIFI_DRIVER_MODULE_PATH                     := "/system/lib/modules/dhd.ko"
WIFI_DRIVER_MODULE_NAME                     := "dhd"
WIFI_DRIVER_MODULE_ARG                      := "firmware_path=/system/etc/wifi/bcmdhd_sta.bin nvram_path=/system/etc/wifi/nvram_net.txt"
WIFI_DRIVER_MODULE_AP_ARG                   := "firmware_path=/system/etc/wifi/bcmdhd_apsta.bin nvram_path=/system/etc/wifi/nvram_net.txt"
WIFI_BAND                                   := 802_11_ABG

# Bluetooth
BOARD_HAVE_BLUETOOTH := true
BOARD_HAVE_BLUETOOTH_BCM := true
BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := device/samsung/cs02/bluetooth
BOARD_BLUEDROID_VENDOR_CONF := device/samsung/cs02/bluetooth/libbt_vndcfg.txt

# RIL
BOARD_RIL_CLASS := ../../../device/samsung/cs02/ril/

# CMHW
BOARD_HARDWARE_CLASS := hardware/samsung/cmhw/ device/samsung/cs02/cmhw/

# Resolution
TARGET_SCREEN_HEIGHT := 800
TARGET_SCREEN_WIDTH := 480
DEVICE_RESOLUTION := 480x800

# Enable WEBGL in WebKit
ENABLE_WEBGL                := true
