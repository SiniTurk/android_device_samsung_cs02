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

# Kernel
BOARD_KERNEL_BASE := 0x82000000
BOARD_KERNEL_PAGESIZE := 4096
TARGET_PREBUILT_KERNEL := device/samsung/cs02/kernel
TARGET_KERNEL_CONFIG := bcm21664_hawaii_ss_cs02_rev02_defconfig
TARGET_KERNEL_SOURCE := kernel/samsung/cs02/
# Make sure you have the correct toolchain available
TARGET_KERNEL_TOOLCHAIN_VERSION := 4.6
KERNEL_TOOLCHAIN_PREFIX := arm-eabi-
KERNEL_TOOLCHAIN := $(ANDROID_BUILD_TOP)/prebuilts/gcc/$(HOST_OS)-x86/arm/$(KERNEL_TOOLCHAIN_PREFIX)$(TARGET_KERNEL_TOOLCHAIN_VERSION)/bin

# Partition sizes
BOARD_BOOTIMAGE_PARTITION_SIZE := 8388608 # mmcblk0p5, 8192 blocks
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 8388608 # mmcblk0p6, 8192 blocks
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1210769408 # mmcblk0p17, 1182392 blocks
BOARD_USERDATAIMAGE_PARTITION_SIZE := 2373976064 # mmcblk0p19, 2318336 blocks
BOARD_FLASH_BLOCK_SIZE := 1024

# Recovery
TARGET_RECOVERY_FSTAB := device/samsung/cs02/ramdisk/fstab.hawaii_ss_cs02
BOARD_HAS_NO_SELECT_BUTTON := true

# SELinux
BOARD_SEPOLICY_DIRS += \
    device/samsung/cs02/sepolicy
